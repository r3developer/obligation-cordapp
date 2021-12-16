package net.corda.samples.obligation.flows;

import co.paralleluniverse.fibers.Suspendable;
import net.corda.core.contracts.*;
import net.corda.core.crypto.SecureHash;
import net.corda.core.flows.*;
import net.corda.core.identity.Party;
import net.corda.core.node.services.Vault;
import net.corda.core.node.services.vault.QueryCriteria;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import net.corda.core.utilities.ProgressTracker;
import net.corda.samples.obligation.contracts.IOUContract;
import net.corda.samples.obligation.states.IOUState;

import java.lang.IllegalArgumentException;
import java.security.PublicKey;
import java.util.*;
import java.util.List;
import java.util.UUID;

public class IOUSettleFlow {

    /**
     * This is the flows which handles the settlement (partial or complete) of existing IOUs on the ledger.
     * Gathering the counter-party's signature is handled by the [CollectSignaturesFlow].
     * Notarisation (if required) and commitment to the ledger is handled by the [FinalityFlow].
     * The flows returns the [SignedTransaction] that was committed to the ledger.
     */
    @InitiatingFlow
    @StartableByRPC
    public static class InitiatorFlow extends FlowLogic<SignedTransaction> {

        private final UniqueIdentifier stateLinearId;
        private final int pay_amount;

        public InitiatorFlow(UniqueIdentifier stateLinearId, int pay_amount) {
            this.stateLinearId = stateLinearId;
            this.pay_amount = pay_amount;
        }

        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {

            // 1. Retrieve the IOU State from the vault using LinearStateQueryCriteria
            List<UUID> listOfLinearIds = Arrays.asList(stateLinearId.getId());
            QueryCriteria queryCriteria = new QueryCriteria.LinearStateQueryCriteria(null, listOfLinearIds);
            Vault.Page results = getServiceHub().getVaultService().queryBy(IOUState.class, queryCriteria);
            StateAndRef inputStateAndRefToSettle = (StateAndRef) results.getStates().get(0);
            IOUState inputStateToSettle = (IOUState) ((StateAndRef) results.getStates().get(0)).getState().getData();
            Party counterparty = inputStateToSettle.getLender();

            // Step 2. Check the party running this flows is the borrower.
            if (!inputStateToSettle.getBorrower().getOwningKey().equals(getOurIdentity().getOwningKey())) {
                throw new IllegalArgumentException("The borrower must issue the flows");
            }
            // Step 3. Create a transaction builder.

            // Obtain a reference to a notary we wish to use.
            /** Explicit selection of notary by CordaX500Name - argument can by coded in flows or parsed from config
             */
            final Party notary = getServiceHub().getNetworkMapCache().getNotaryIdentities().get(0); // METHOD 1
            // final Party notary = getServiceHub().getNetworkMapCache().getNotary(CordaX500Name.parse("O=Notary,L=London,C=GB")); // METHOD 2

            TransactionBuilder tb = new TransactionBuilder(notary);


            // Step 4. Add the IOU input states and settle command to the transaction builder.

            ArrayList<PublicKey> listOfKeys = new ArrayList<>();
            listOfKeys.add(counterparty.getOwningKey());
            listOfKeys.add(getOurIdentity().getOwningKey());
            Command<IOUContract.Commands.Settle> command = new Command<>(
                    new IOUContract.Commands.Settle(),
                    listOfKeys
            );
            tb.addCommand(command);
            tb.addInputState(inputStateAndRefToSettle);

            // Step 5. Only add an output IOU states if the IOU has not been fully settled.
            if (pay_amount < (inputStateToSettle.getAmount() - inputStateToSettle.getPaid())) {

                IOUState opState = new IOUState(inputStateToSettle.getAmount(),inputStateToSettle.getLender(), inputStateToSettle.getBorrower(),inputStateToSettle.getPaid()+pay_amount, inputStateToSettle.getLinearId());
                tb.addOutputState(opState, IOUContract.IOU_CONTRACT_ID);
            }

            // Step 8. Verify and sign the transaction.
            tb.verify(getServiceHub());
            final SignedTransaction ptx = getServiceHub().signInitialTransaction(tb);


            // Step 6. Collect the other party's signature using the CollectSignaturesFlow.Each required signer will need to
            // respond by invoking its own SignTransactionFlow subclass to check the transaction (by implementing the checkTransaction method)
            // and provide their signature if they are satisfied.
            List<Party> otherParties = new ArrayList<Party>();
            otherParties.add(inputStateToSettle.getLender());
            otherParties.add(inputStateToSettle.getBorrower());

            otherParties.remove(getOurIdentity());

            List<FlowSession> sessions = new ArrayList<>();
            for (Party otherParty: otherParties) {
                sessions.add(initiateFlow(otherParty));
            }

            SignedTransaction stx = subFlow(new CollectSignaturesFlow(ptx, sessions));

            // Step 7. Assuming no exceptions, we can now finalise the transaction
            return subFlow(new FinalityFlow(stx, sessions));

        }

    }

    /**
     * This is the flows which signs IOU settlements.
     * The signing is handled by the [SignTransactionFlow].
     */
    @InitiatedBy(IOUSettleFlow.InitiatorFlow.class)
    public static class Responder extends FlowLogic<SignedTransaction> {

        private final FlowSession otherPartyFlow;
        private SecureHash txWeJustSignedId;

        public Responder(FlowSession otherPartyFlow) {
            this.otherPartyFlow = otherPartyFlow;
        }

        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {
            class SignTxFlow extends SignTransactionFlow {
                private SignTxFlow(FlowSession otherPartyFlow, ProgressTracker progressTracker) {
                    super(otherPartyFlow, progressTracker);
                }

                @Override
                protected void checkTransaction(SignedTransaction stx) {
                    // Once the transaction has verified, initialize txWeJustSignedID variable.
                    txWeJustSignedId = stx.getId();
                }
            }

            // Create a sign transaction flows
            SignTxFlow signTxFlow = new SignTxFlow(otherPartyFlow, SignTransactionFlow.Companion.tracker());

            // Run the sign transaction flows to sign the transaction
            subFlow(signTxFlow);

            // Run the ReceiveFinalityFlow to finalize the transaction and persist it to the vault.
            return subFlow(new ReceiveFinalityFlow(otherPartyFlow, txWeJustSignedId));

        }
    }


}
