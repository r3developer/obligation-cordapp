package net.corda.samples.obligation.contracts;


import net.corda.core.contracts.*;
import net.corda.finance.*;
import net.corda.samples.obligation.states.IOUStateTests;
import net.corda.testing.contracts.DummyState;
import net.corda.testing.node.MockServices;

import static net.corda.testing.node.NodeTestUtils.ledger;

import net.corda.samples.obligation.TestUtils;
import net.corda.core.transactions.LedgerTransaction;

import java.util.Arrays;

import net.corda.samples.obligation.states.IOUState;
import org.junit.*;


/**
 * The objective here is to write some contracts code that verifies a transaction to issue an {@link IOUState}.
 * As with the {@link IOUStateTests} uncomment each unit test and run them one at a time. Use the body of the tests and the
 * task description to determine how to get the tests to pass.
 */
public class IOUIssueTests {
    // A pre-defined dummy command.
    public interface Commands extends CommandData {
        class DummyCommand extends TypeOnlyCommandData implements Commands {
        }
    }

    static private final MockServices ledgerServices = new MockServices(
            Arrays.asList("net.corda.samples.obligation.contracts")
    );


    @Test
    public void mustIncludeIssueCommand() {
        IOUState iou = new IOUState(10, TestUtils.ALICE.getParty(), TestUtils.BOB.getParty());

        ledger(ledgerServices, l -> {
            l.transaction(tx -> {
                tx.output(IOUContract.IOU_CONTRACT_ID, iou);
                tx.command(Arrays.asList(TestUtils.ALICE.getPublicKey(), TestUtils.BOB.getPublicKey()), new Commands.DummyCommand()); // Wrong type.
                return tx.failsWith("Contract verification failed");
            });
            l.transaction(tx -> {
                tx.output(IOUContract.IOU_CONTRACT_ID, iou);
                tx.command(Arrays.asList(TestUtils.ALICE.getPublicKey(), TestUtils.BOB.getPublicKey()), new IOUContract.Commands.Issue()); // Correct type.
                return tx.verifies();
            });
            return null;
        });
    }


    @Test
    public void issueTransactionMustHaveNoInputs() {
        IOUState iou = new IOUState(1, TestUtils.ALICE.getParty(), TestUtils.BOB.getParty());

        ledger(ledgerServices, l -> {
            l.transaction(tx -> {
                tx.input(IOUContract.IOU_CONTRACT_ID, new DummyState());
                tx.command(Arrays.asList(TestUtils.ALICE.getPublicKey(), TestUtils.BOB.getPublicKey()), new IOUContract.Commands.Issue());
                tx.output(IOUContract.IOU_CONTRACT_ID, iou);
                return tx.failsWith("No inputs should be consumed when issuing an IOU");
            });
            l.transaction(tx -> {
                tx.output(IOUContract.IOU_CONTRACT_ID, iou);
                tx.command(Arrays.asList(TestUtils.ALICE.getPublicKey(), TestUtils.BOB.getPublicKey()), new IOUContract.Commands.Issue());
                return tx.verifies(); // As there are no input sates
            });
            return null;
        });
    }


    @Test
    public void issueTransactionMustHaveOneOutput() {
        IOUState iou = new IOUState(1, TestUtils.ALICE.getParty(), TestUtils.BOB.getParty());
        ledger(ledgerServices, l -> {
            l.transaction(tx -> {
                tx.command(Arrays.asList(TestUtils.ALICE.getPublicKey(), TestUtils.BOB.getPublicKey()), new IOUContract.Commands.Issue());
                tx.output(IOUContract.IOU_CONTRACT_ID, iou); // Two outputs fails.
                tx.output(IOUContract.IOU_CONTRACT_ID, iou);
                return tx.failsWith("Only one output states should be created when issuing an IOU.");
            });
            l.transaction(tx -> {
                tx.command(Arrays.asList(TestUtils.ALICE.getPublicKey(), TestUtils.BOB.getPublicKey()), new IOUContract.Commands.Issue());
                tx.output(IOUContract.IOU_CONTRACT_ID, iou); // One output passes.
                return tx.verifies();
            });
            return null;
        });
    }


    @Test
    public void cannotCreateZeroValueIOUs() {
        ledger(ledgerServices, l -> {
            l.transaction(tx -> {
                tx.command(Arrays.asList(TestUtils.ALICE.getPublicKey(), TestUtils.BOB.getPublicKey()), new IOUContract.Commands.Issue());
                tx.output(IOUContract.IOU_CONTRACT_ID, new IOUState(0, TestUtils.ALICE.getParty(), TestUtils.BOB.getParty())); // Zero amount fails.
                return tx.failsWith("A newly issued IOU must have a positive amount.");
            });
            l.transaction(tx -> {
                tx.command(Arrays.asList(TestUtils.ALICE.getPublicKey(), TestUtils.BOB.getPublicKey()), new IOUContract.Commands.Issue());
                tx.output(IOUContract.IOU_CONTRACT_ID, new IOUState(10, TestUtils.ALICE.getParty(), TestUtils.BOB.getParty()));
                return tx.verifies();
            });
            return null;
        });
    }


    @Test
    public void lenderAndBorrowerCannotBeTheSame() {
        IOUState iou = new IOUState(10, TestUtils.ALICE.getParty(), TestUtils.BOB.getParty());
        IOUState borrowerIsLenderIou = new IOUState(10, TestUtils.ALICE.getParty(), TestUtils.ALICE.getParty());
        ledger(ledgerServices, l -> {
            l.transaction(tx -> {
                tx.command(Arrays.asList(TestUtils.ALICE.getPublicKey(), TestUtils.BOB.getPublicKey()), new IOUContract.Commands.Issue());
                tx.output(IOUContract.IOU_CONTRACT_ID, borrowerIsLenderIou);
                return tx.failsWith("The lender and borrower cannot have the same identity.");
            });
            l.transaction(tx -> {
                tx.command(Arrays.asList(TestUtils.ALICE.getPublicKey(), TestUtils.BOB.getPublicKey()), new IOUContract.Commands.Issue());
                tx.output(IOUContract.IOU_CONTRACT_ID, iou);
                return tx.verifies();
            });
            return null;
        });
    }


    @Test
    public void lenderAndBorrowerMustSignIssueTransaction() {
        IOUState iou = new IOUState(10, TestUtils.ALICE.getParty(), TestUtils.BOB.getParty());
        ledger(ledgerServices, l -> {
            l.transaction(tx -> {
                tx.command(TestUtils.DUMMY.getPublicKey(), new IOUContract.Commands.Issue());
                tx.output(IOUContract.IOU_CONTRACT_ID, iou);
                return tx.failsWith("Both lender and borrower together only may sign IOU issue transaction.");
            });
            l.transaction(tx -> {
                tx.command(TestUtils.ALICE.getPublicKey(), new IOUContract.Commands.Issue());
                tx.output(IOUContract.IOU_CONTRACT_ID, iou);
                return tx.failsWith("Both lender and borrower together only may sign IOU issue transaction.");
            });
            l.transaction(tx -> {
                tx.command(TestUtils.BOB.getPublicKey(), new IOUContract.Commands.Issue());
                tx.output(IOUContract.IOU_CONTRACT_ID, iou);
                return tx.failsWith("Both lender and borrower together only may sign IOU issue transaction.");
            });
            l.transaction(tx -> {
                tx.command(Arrays.asList(TestUtils.BOB.getPublicKey(), TestUtils.BOB.getPublicKey(), TestUtils.BOB.getPublicKey()), new IOUContract.Commands.Issue());
                tx.output(IOUContract.IOU_CONTRACT_ID, iou);
                return tx.failsWith("Both lender and borrower together only may sign IOU issue transaction.");
            });
            l.transaction(tx -> {
                tx.command(Arrays.asList(TestUtils.BOB.getPublicKey(), TestUtils.BOB.getPublicKey(), TestUtils.MINICORP.getPublicKey(), TestUtils.ALICE.getPublicKey()), new IOUContract.Commands.Issue());
                tx.output(IOUContract.IOU_CONTRACT_ID, iou);
                return tx.failsWith("Both lender and borrower together only may sign IOU issue transaction.");
            });
            l.transaction(tx -> {
                tx.command(Arrays.asList(TestUtils.BOB.getPublicKey(), TestUtils.BOB.getPublicKey(), TestUtils.BOB.getPublicKey(), TestUtils.ALICE.getPublicKey()), new IOUContract.Commands.Issue());
                tx.output(IOUContract.IOU_CONTRACT_ID, iou);
                return tx.verifies();
            });
            l.transaction(tx -> {
                tx.command(Arrays.asList(TestUtils.ALICE.getPublicKey(), TestUtils.BOB.getPublicKey()), new IOUContract.Commands.Issue());
                tx.output(IOUContract.IOU_CONTRACT_ID, iou);
                return tx.verifies();
            });
            return null;
        });
    }
}
