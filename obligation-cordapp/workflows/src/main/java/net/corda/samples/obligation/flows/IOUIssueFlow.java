package net.corda.samples.obligation.flows;

import co.paralleluniverse.fibers.Suspendable;

import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Arrays;
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

        private final IOUState state;
        public InitiatorFlow(IOUState iouState) {

            this.state = iouState;

        }
        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {
            // Step 1. Get a reference to the notary service on our network and our key pair.

            /** Explicit selection of notary by CordaX500Name - argument can by coded in flows or parsed from config
             */
            final Party notary = getServiceHub().getNetworkMapCache().getNotary(CordaX500Name.parse("O=Notary,L=London,C=GB"));
            if (notary == null) {
                throw new FlowException("The desired notary is not known" );
            }

            // Generate an unsigned transaction
            Party me = getOurIdentity();
            // Step 2. Create a new issue command.
            // Remember that a command is a CommandData object and a list of CompositeKeys
            List<PublicKey> listOfKeys = new ArrayList<>();
            listOfKeys.add(state.getLender().getOwningKey());
            listOfKeys.add(state.getBorrower().getOwningKey());
            final Command<Issue> issueCommand = new Command<>(new Issue(), listOfKeys);

            // Step 3. Create a new TransactionBuilder object.
            final TransactionBuilder builder = new TransactionBuilder(notary);

            // Step 4. Add the iou as an output states, as well as a command to the transaction builder.
            builder.addOutputState(state, IOUContract.IOU_CONTRACT_ID);
            builder.addCommand(issueCommand);

            // Step 5. Verify and sign it with our KeyPair.
            builder.verify(getServiceHub());
            final SignedTransaction ptx = getServiceHub().signInitialTransaction(builder);

            // Step 6. Collect the other party's signature using the CollectSignaturesFlow.Each required signer will need to
            // respond by invoking its own SignTransactionFlow subclass to check the transaction (by implementing the checkTransaction method)
            // and provide their signature if they are satisfied.
            List<Party> otherParties = new ArrayList<Party>();
            otherParties.add(state.getLender());
            otherParties.add(state.getBorrower());
            otherParties.remove(getOurIdentity());

            // Collect all of the required signatures from other Corda nodes using the CollectSignaturesFlow
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

                private SignTxFlow(FlowSession flowSession) {
                    super(flowSession);
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
            SignTxFlow signTxFlow = new SignTxFlow(flowSession);

            // Run the sign transaction flows to sign the transaction
            subFlow(signTxFlow);

            // Run the ReceiveFinalityFlow to finalize the transaction and persist it to the vault. As the initiator
            // flow has already called the Finality flow, we call the ReceiveFinalityFlow and not the FinalityFlow as only one
            // party needs to call the FinalityFlow.
            return subFlow(new ReceiveFinalityFlow(flowSession, txWeJustSigned));

        }
    }
}
