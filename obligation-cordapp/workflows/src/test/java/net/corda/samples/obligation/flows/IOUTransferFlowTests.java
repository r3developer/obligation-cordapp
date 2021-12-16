package net.corda.samples.obligation.flows;

import net.corda.core.concurrent.CordaFuture;
import net.corda.core.contracts.Command;
import net.corda.core.contracts.StateRef;
import net.corda.core.identity.CordaX500Name;
import net.corda.core.identity.Party;
import net.corda.core.transactions.SignedTransaction;
import net.corda.finance.Currencies;
import net.corda.testing.node.*;
import net.corda.samples.obligation.states.IOUState;
import net.corda.samples.obligation.contracts.IOUContract;
import org.junit.*;
import org.junit.rules.ExpectedException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class IOUTransferFlowTests {

    private MockNetwork mockNetwork;
    private StartedMockNode a, b, c;

    @Before
    public void setup() {
        MockNetworkParameters mockNetworkParameters = new MockNetworkParameters().withCordappsForAllNodes(
                Arrays.asList(
                        TestCordapp.findCordapp("net.corda.samples.obligation.contracts")
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
        startedNodes.forEach(el -> el.registerInitiatedFlow(IOUTransferFlow.Responder.class));
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
        Party lender = b.getInfo().getLegalIdentitiesAndCerts().get(0).getParty();
        SignedTransaction stx = issueIOU(10, lender);
        IOUState inputIou = (IOUState) stx.getTx().getOutputs().get(0).getData();
        IOUTransferFlow.InitiatorFlow flow = new IOUTransferFlow.InitiatorFlow(inputIou.getLinearId(), c.getInfo().getLegalIdentities().get(0));
        Future<SignedTransaction> future = b.startFlow(flow);

        mockNetwork.runNetwork();

        SignedTransaction ptx = future.get();

        // Check the transaction is well formed...
        // One output IOUState, one input states reference and a Transfer command with the right properties.
        assert (ptx.getTx().getInputs().size() == 1);
        assert (ptx.getTx().getOutputs().size() == 1);
        assert (ptx.getTx().getOutputs().get(0).getData() instanceof IOUState);
        assert (ptx.getTx().getInputs().get(0).equals(new StateRef(stx.getId(), 0)));

        IOUState outputIOU = (IOUState) ptx.getTx().getOutput(0);
        Command command = ptx.getTx().getCommands().get(0);

        assert (command.getValue().equals(new IOUContract.Commands.Transfer()));
        ptx.verifySignaturesExcept(a.getInfo().getLegalIdentities().get(0).getOwningKey(), c.getInfo().getLegalIdentities().get(0).getOwningKey(), mockNetwork.getDefaultNotaryIdentity().getOwningKey());
    }


    @Test
    public void flowCanOnlyBeRunByCurrentLender() throws Exception {
        Party lender = b.getInfo().getLegalIdentitiesAndCerts().get(0).getParty();
        SignedTransaction stx = issueIOU(10, lender);
        IOUState inputIou = (IOUState) stx.getTx().getOutputs().get(0).getData();
        IOUTransferFlow.InitiatorFlow flow = new IOUTransferFlow.InitiatorFlow(inputIou.getLinearId(), c.getInfo().component2().get(0).getParty());
        Future<SignedTransaction> future = a.startFlow(flow);
        try {
            mockNetwork.runNetwork();
            future.get();
        } catch (Exception exception) {
            assert exception.getMessage().equals("java.lang.IllegalArgumentException: This flows must be run by the current lender.");
        }
    }


    @Test
    public void iouCannotBeTransferredToSameParty() throws Exception {
        Party lender = b.getInfo().getLegalIdentitiesAndCerts().get(0).getParty();
        Party borrower = a.getInfo().getLegalIdentitiesAndCerts().get(0).getParty();
        SignedTransaction stx = issueIOU(10, lender);
        IOUState inputIou = (IOUState) stx.getTx().getOutputs().get(0).getData();
        IOUTransferFlow.InitiatorFlow flow = new IOUTransferFlow.InitiatorFlow(inputIou.getLinearId(), c.getInfo().component2().get(0).getParty());
        Future<SignedTransaction> future = b.startFlow(flow);
        try {
            mockNetwork.runNetwork();
            future.get();
        } catch (Exception exception) {
            System.out.println(exception.getMessage());
            assert exception.getMessage().equals("Contract verification failed: Failed requirement: The lender property must change in a transfer.");
        }
    }


    @Test
    public void flowReturnsTransactionSignedByAllParties() throws Exception {
        Party lender = b.getInfo().getLegalIdentitiesAndCerts().get(0).getParty();
        Party newLender = c.getInfo().getLegalIdentitiesAndCerts().get(0).getParty();
        SignedTransaction stx = issueIOU(10, lender);
        IOUState inputIou = (IOUState) stx.getTx().getOutputs().get(0).getData();
        IOUTransferFlow.InitiatorFlow flow = new IOUTransferFlow.InitiatorFlow(inputIou.getLinearId(), newLender);
        Future<SignedTransaction> future = a.startFlow(flow);
        try {
            mockNetwork.runNetwork();
            future.get();
            stx.verifySignaturesExcept(mockNetwork.getDefaultNotaryIdentity().getOwningKey());
        } catch (Exception exception) {
            System.out.println(exception.getMessage());
        }
    }


    @Test
    public void flowReturnsTransactionSignedByAllPartiesAndNotary() throws Exception {
        Party lender = b.getInfo().getLegalIdentitiesAndCerts().get(0).getParty();
        SignedTransaction stx = issueIOU(10, lender);
        IOUState inputIou = (IOUState) stx.getTx().getOutputs().get(0).getData();
        IOUTransferFlow.InitiatorFlow flow = new IOUTransferFlow.InitiatorFlow(inputIou.getLinearId(), c.getInfo().component2().get(0).getParty());
        Future<SignedTransaction> future = a.startFlow(flow);
        try {
            mockNetwork.runNetwork();
            future.get();
            stx.verifyRequiredSignatures();
        } catch (Exception exception) {
            System.out.println(exception.getMessage());
        }
    }
}
