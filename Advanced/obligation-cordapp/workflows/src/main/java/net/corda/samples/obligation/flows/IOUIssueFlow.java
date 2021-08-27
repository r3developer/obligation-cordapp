package net.corda.samples.obligation.flows;

import co.paralleluniverse.fibers.Suspendable;

import java.util.Currency;
import java.util.List;
import java.util.stream.Collectors;

import net.corda.core.contracts.Amount;
import net.corda.core.contracts.Command;
import net.corda.core.contracts.ContractState;
import net.corda.core.crypto.SecureHash;
import net.corda.core.flows.*;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.Party;
import net.corda.core.identity.CordaX500Name;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import static net.corda.core.contracts.ContractsDSL.requireThat;
import net.corda.core.utilities.ProgressTracker;
import net.corda.core.contracts.UniqueIdentifier;

import net.corda.samples.obligation.contracts.IOUContract;
import net.corda.samples.obligation.states.IOUState;
import org.jetbrains.annotations.NotNull;

import static net.corda.samples.obligation.contracts.IOUContract.Commands.*;

/**
 * This is the flows which handles issuance of new IOUs on the ledger.
 * Gathering the counter-party's signature is handled by the [CollectSignaturesFlow].
 * Notarisation (if required) and commitment to the ledger is handled by the [FinalityFlow].
 * The flows returns the [SignedTransaction] that was committed to the ledger.
 */
public class IOUIssueFlow {

    @InitiatingFlow
    @StartableByRPC
    public static class InitiatorFlow extends FlowLogic<SignedTransaction> {
        @NotNull
        private final Amount<Currency> amount;
        @NotNull
        private final Party lender;


        public InitiatorFlow(@NotNull final Amount<Currency> amount, @NotNull final Party lender) {

            this.amount = amount;
            this.lender = lender;

        }

        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {
            // Step 1. Get a reference to the notary service on our network and our key pair.

            /** METHOD 1: Take first notary on network, WARNING: use for test, non-prod environments, and single-notary networks only!*
             *  METHOD 2: Explicit selection of notary by CordaX500Name - argument can by coded in flows or parsed from config (Preferred)
             *
             *  * - For production you always want to use Method 2 as it guarantees the expected notary is returned.
             */
            //final Party notary = getServiceHub().getNetworkMapCache().getNotaryIdentities().get(0); // METHOD 1
            final Party notary = getServiceHub().getNetworkMapCache().getNotary(CordaX500Name.parse("O=Notary,L=London,C=GB")); // METHOD 2

            // Generate an unsigned transaction
            Party me = getOurIdentity();
            IOUState iouState = new IOUState(amount, lender, me, new Amount<Currency>(0, amount.getToken()) , new UniqueIdentifier());
            // Step 2. Create a new issue command.
            // Remember that a command is a CommandData object and a list of CompositeKeys
            final Command<Issue> issueCommand = new Command<>(
                    new Issue(), iouState.getParticipants()
                    .stream().map(AbstractParty::getOwningKey)
                    .collect(Collectors.toList()));

            // Step 3. Create a new TransactionBuilder object.
            final TransactionBuilder builder = new TransactionBuilder(notary);

            // Step 4. Add the iou as an output states, as well as a command to the transaction builder.
            builder.addOutputState(iouState, IOUContract.IOU_CONTRACT_ID);
            builder.addCommand(issueCommand);


            // Step 5. Verify and sign it with our KeyPair.
            builder.verify(getServiceHub());
            final SignedTransaction ptx = getServiceHub().signInitialTransaction(builder);


            // Step 6. Collect the other party's signature using the SignTransactionFlow.
            List<Party> otherParties = iouState.getParticipants()
                    .stream().map(el -> (Party)el)
                    .collect(Collectors.toList());

            otherParties.remove(getOurIdentity());

            List<FlowSession> sessions = otherParties
                    .stream().map(el -> initiateFlow(el))
                    .collect(Collectors.toList());

            SignedTransaction stx = subFlow(new CollectSignaturesFlow(ptx, sessions));

            // Step 7. Assuming no exceptions, we can now finalise the transaction
            return subFlow(new FinalityFlow(stx, sessions));
        }
    }

    /**
     * This is the flows which signs IOU issuance.
     * The signing is handled by the [SignTransactionFlow].
     */
    @InitiatedBy(IOUIssueFlow.InitiatorFlow.class)
    public static class ResponderFlow extends FlowLogic<SignedTransaction> {

        private final FlowSession flowSession;
        private SecureHash txWeJustSigned;

        public ResponderFlow(FlowSession flowSession){
            this.flowSession = flowSession;
        }

        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {

            class SignTxFlow extends SignTransactionFlow {

                private SignTxFlow(FlowSession flowSession, ProgressTracker progressTracker) {
                    super(flowSession, progressTracker);
                }

                @Override
                protected void checkTransaction(SignedTransaction stx) {
                    requireThat(req -> {
                        ContractState output = stx.getTx().getOutputs().get(0).getData();
                        req.using("This must be an IOU transaction", output instanceof IOUState);
                        return null;
                    });
                    // Once the transaction has verified, initialize txWeJustSignedID variable.
                    txWeJustSigned = stx.getId();
                }
            }

            flowSession.getCounterpartyFlowInfo().getFlowVersion();

            // Create a sign transaction flows
            SignTxFlow signTxFlow = new SignTxFlow(flowSession, SignTransactionFlow.Companion.tracker());

            // Run the sign transaction flows to sign the transaction
            subFlow(signTxFlow);

            // Run the ReceiveFinalityFlow to finalize the transaction and persist it to the vault.
            return subFlow(new ReceiveFinalityFlow(flowSession, txWeJustSigned));

        }
    }
}
