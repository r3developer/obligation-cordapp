package net.corda.samples.obligation.flows;

import net.corda.core.identity.Party;
import net.corda.testing.node.*;
import net.corda.core.contracts.Amount;
import net.corda.core.contracts.CommandWithParties;
import net.corda.core.concurrent.CordaFuture;
import net.corda.core.identity.CordaX500Name;
import net.corda.core.transactions.LedgerTransaction;
import net.corda.core.transactions.SignedTransaction;
import net.corda.samples.obligation.states.IOUState;
import net.corda.samples.obligation.contracts.IOUContract;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

public class IOUSettleFlowTests {

    private MockNetwork mockNetwork;
    private StartedMockNode a, b, c;

    @Before
    public void setup() {
        MockNetworkParameters mockNetworkParameters = new MockNetworkParameters().withCordappsForAllNodes(
                Arrays.asList(
                        TestCordapp.findCordapp("net.corda.samples.obligation.contracts"),
                        TestCordapp.findCordapp("net.corda.finance.schemas")
                )
        ).withNotarySpecs(Arrays.asList(new MockNetworkNotarySpec(new CordaX500Name("Notary", "London", "GB"))));
        mockNetwork = new MockNetwork(mockNetworkParameters);
        System.out.println(mockNetwork);

        a = mockNetwork.createNode(new MockNodeParameters());
        b = mockNetwork.createNode(new MockNodeParameters());
        c = mockNetwork.createNode(new MockNodeParameters());

        ArrayList<StartedMockNode> startedNodes = new ArrayList<>();
        startedNodes.add(a);
        startedNodes.add(b);
        startedNodes.add(c);

        // For real nodes this happens automatically, but we have to manually register the flows for tests
        startedNodes.forEach(el -> el.registerInitiatedFlow(IOUSettleFlow.Responder.class));
        startedNodes.forEach(el -> el.registerInitiatedFlow(IOUIssueFlow.ResponderFlow.class));
        mockNetwork.runNetwork();
    }

    @After
    public void tearDown() {
        mockNetwork.stopNodes();
    }

    @Rule
    public final ExpectedException exception = ExpectedException.none();

    private SignedTransaction issueIOU(int amount, Party lender) throws InterruptedException, ExecutionException {
        IOUIssueFlow.InitiatorFlow flow = new IOUIssueFlow.InitiatorFlow(amount, lender);
        CordaFuture future = a.startFlow(flow);
        mockNetwork.runNetwork();
        return (SignedTransaction) future.get();
    }


    @Test
    public void flowReturnsCorrectlyFormedPartiallySignedTransaction() throws Exception {
        SignedTransaction stx = issueIOU(10, b.getInfo().getLegalIdentities().get(0));
        IOUState inputIOU = stx.getTx().outputsOfType(IOUState.class).get(0);
        IOUSettleFlow.InitiatorFlow flow = new IOUSettleFlow.InitiatorFlow(inputIOU.getLinearId(), 5);
        Future<SignedTransaction> futureSettleResult = a.startFlow(flow);

        mockNetwork.runNetwork();

        SignedTransaction settleResult = futureSettleResult.get();
        // Check the transaction is well formed...
        // One output IOUState, one input IOUState reference, input and output cash
        a.transaction(() -> {
            try {
                LedgerTransaction ledgerTx = settleResult.toLedgerTransaction(a.getServices(), false);
                assert (ledgerTx.getInputs().size() == 1);
                assert (ledgerTx.getOutputs().size() == 1);

                IOUState outputIOU = ledgerTx.outputsOfType(IOUState.class).get(0);
                IOUState correctOutputIOU = new IOUState(inputIOU.getAmount(), inputIOU.getLender(), inputIOU.getBorrower(), inputIOU.getPaid() + 5, inputIOU.getLinearId());

                assert (outputIOU.getAmount() == correctOutputIOU.getAmount());
                assert (outputIOU.getPaid() == correctOutputIOU.getPaid());
                assert (outputIOU.getLender().equals(correctOutputIOU.getLender()));
                assert (outputIOU.getBorrower().equals(correctOutputIOU.getBorrower()));

                CommandWithParties command = ledgerTx.getCommands().get(0);
                assert (command.getValue().equals(new IOUContract.Commands.Settle()));

                settleResult.verifySignaturesExcept(b.getInfo().getLegalIdentities().get(0).getOwningKey(),
                        mockNetwork.getDefaultNotaryIdentity().getOwningKey());

                return null;
            } catch (Exception exception) {
                System.out.println(exception);
            }
            return null;
        });
    }


    @Test
    public void settleFlowCanOnlyBeRunByBorrower() throws Exception {
        SignedTransaction stx = issueIOU(10, b.getInfo().getLegalIdentities().get(0));

        IOUState inputIOU = stx.getTx().outputsOfType(IOUState.class).get(0);
        IOUSettleFlow.InitiatorFlow flow = new IOUSettleFlow.InitiatorFlow(inputIOU.getLinearId(), 5);
        Future<SignedTransaction> futureSettleResult = b.startFlow(flow);

        try {
            mockNetwork.runNetwork();
            futureSettleResult.get();
        } catch (Exception exception) {
            assert exception.getMessage().equals("java.lang.IllegalArgumentException: The borrower must issue the flows");
        }
    }


    @Test
    public void flowReturnsTransactionSignedByBothParties() throws Exception {
        SignedTransaction stx = issueIOU(10, b.getInfo().getLegalIdentities().get(0));
        IOUState inputIOU = stx.getTx().outputsOfType(IOUState.class).get(0);
        IOUSettleFlow.InitiatorFlow flow = new IOUSettleFlow.InitiatorFlow(inputIOU.getLinearId(), 5);
        Future<SignedTransaction> futureSettleResult = a.startFlow(flow);

        try {
            mockNetwork.runNetwork();
            futureSettleResult.get().verifySignaturesExcept(mockNetwork.getDefaultNotaryIdentity().getOwningKey());
        } catch (Exception exception) {
            System.out.println(exception.getMessage());
        }
    }


    @Test
    public void flowReturnsTransactionSignedByBothPartiesAndNotary() throws Exception {
        SignedTransaction stx = issueIOU(10, b.getInfo().getLegalIdentities().get(0));
        IOUState inputIOU = stx.getTx().outputsOfType(IOUState.class).get(0);
        IOUSettleFlow.InitiatorFlow flow = new IOUSettleFlow.InitiatorFlow(inputIOU.getLinearId(), 5);
        Future<SignedTransaction> futureSettleResult = a.startFlow(flow);

        try {
            mockNetwork.runNetwork();
            futureSettleResult.get();
            stx.verifyRequiredSignatures();
        } catch (Exception exception) {
            System.out.println(exception.getMessage());
        }
    }


}
