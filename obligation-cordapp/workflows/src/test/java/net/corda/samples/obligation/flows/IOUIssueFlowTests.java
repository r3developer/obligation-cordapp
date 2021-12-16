package net.corda.samples.obligation.flows;

import net.corda.core.contracts.Command;
import net.corda.core.contracts.TransactionVerificationException;
import net.corda.core.identity.CordaX500Name;
import net.corda.core.transactions.SignedTransaction;
import net.corda.finance.*;
import net.corda.samples.obligation.states.IOUState;
import net.corda.samples.obligation.contracts.IOUContract;
import net.corda.testing.node.*;
import net.corda.core.identity.Party;
import net.corda.core.crypto.SecureHash;
import net.corda.core.flows.*;
import net.corda.core.transactions.TransactionBuilder;

import java.util.stream.Collectors;
import java.util.concurrent.Future;
import java.util.*;

import org.junit.*;
import org.junit.rules.ExpectedException;

import static org.junit.Assert.*;
import static org.hamcrest.core.IsInstanceOf.*;


public class IOUIssueFlowTests {

    private MockNetwork mockNetwork;
    private StartedMockNode a, b;

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

        ArrayList<StartedMockNode> startedNodes = new ArrayList<>();
        startedNodes.add(a);
        startedNodes.add(b);

        // For real nodes this happens automatically, but we have to manually register the flows for tests
        startedNodes.forEach(el -> el.registerInitiatedFlow(IOUIssueFlow.ResponderFlow.class));
        mockNetwork.runNetwork();
    }

    @After
    public void tearDown() {
        mockNetwork.stopNodes();
    }

    @Rule
    public final ExpectedException exception = ExpectedException.none();

    @Test
    public void flowReturnsCorrectlyFormedPartiallySignedTransaction() throws Exception {
        Party lender = a.getInfo().getLegalIdentitiesAndCerts().get(0).getParty();

        IOUIssueFlow.InitiatorFlow flow = new IOUIssueFlow.InitiatorFlow(10, lender);

        Future<SignedTransaction> future = b.startFlow(flow);
        mockNetwork.runNetwork();

        // Return the unsigned(!) SignedTransaction object from the IOUIssueFlow.
        SignedTransaction ptx = future.get();

        // Print the transaction for debugging purposes.
        System.out.println(ptx.getTx());

        // Check the transaction is well formed...
        // No outputs, one input IOUState and a command with the right properties.
        assert (ptx.getTx().getInputs().isEmpty());
        assert (ptx.getTx().getOutputs().get(0).getData() instanceof IOUState);

        Command command = ptx.getTx().getCommands().get(0);
        assert (command.getValue() instanceof IOUContract.Commands.Issue);

        ptx.verifySignaturesExcept(lender.getOwningKey(),
                mockNetwork.getDefaultNotaryNode().getInfo().getLegalIdentitiesAndCerts().get(0).getOwningKey());
    }


    @Test
    public void flowReturnsVerifiedPartiallySignedTransaction() throws Exception {
        // Check that a zero amount IOU fails.
        Party borrower = a.getInfo().getLegalIdentitiesAndCerts().get(0).getParty();
        Party lender = b.getInfo().getLegalIdentitiesAndCerts().get(0).getParty();

        Future<SignedTransaction> futureOne = a.startFlow(new IOUIssueFlow.InitiatorFlow(0, lender));
        mockNetwork.runNetwork();

        exception.expectCause(instanceOf(TransactionVerificationException.class));

        futureOne.get();

        // Check that an IOU with the same participants fails.
        Future<SignedTransaction> futureTwo = b.startFlow(new IOUIssueFlow.InitiatorFlow(10, lender));
        mockNetwork.runNetwork();
        exception.expectCause(instanceOf(TransactionVerificationException.class));
        futureTwo.get();

        // Check a good IOU passes.
        Future<SignedTransaction> futureThree = a.startFlow(new IOUIssueFlow.InitiatorFlow(10, lender));
        mockNetwork.runNetwork();
        futureThree.get();
    }


    @Test
    public void flowReturnsTransactionSignedByBothParties() throws Exception {
        Party lender = a.getInfo().getLegalIdentitiesAndCerts().get(0).getParty();
        Party borrower = b.getInfo().getLegalIdentitiesAndCerts().get(0).getParty();
        IOUIssueFlow.InitiatorFlow flow = new IOUIssueFlow.InitiatorFlow(10, lender);

        Future<SignedTransaction> future = b.startFlow(flow);
        mockNetwork.runNetwork();

        SignedTransaction stx = future.get();
        stx.verifyRequiredSignatures();
    }


    @Test
    public void flowRecordsTheSameTransactionInBothPartyVaults() throws Exception {
        Party lender = a.getInfo().getLegalIdentitiesAndCerts().get(0).getParty();
        Party borrower = b.getInfo().getLegalIdentitiesAndCerts().get(0).getParty();
        IOUIssueFlow.InitiatorFlow flow = new IOUIssueFlow.InitiatorFlow(10, lender);

        Future<SignedTransaction> future = b.startFlow(flow);
        mockNetwork.runNetwork();
        SignedTransaction stx = future.get();
        System.out.printf("Signed transaction hash: %h\n", stx.getId());

        Arrays.asList(a, b).stream().map(el ->
                el.getServices().getValidatedTransactions().getTransaction(stx.getId())
        ).forEach(el -> {
            SecureHash txHash = el.getId();
            System.out.printf("$txHash == %h\n", stx.getId());
            assertEquals(stx.getId(), txHash);
        });
    }
}
