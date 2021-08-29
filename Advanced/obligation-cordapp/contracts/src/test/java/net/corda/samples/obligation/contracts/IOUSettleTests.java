package net.corda.samples.obligation.contracts;


import net.corda.core.contracts.*;
import net.corda.core.contracts.Amount;
import net.corda.core.contracts.CommandData;

import net.corda.core.contracts.TypeOnlyCommandData;
import net.corda.finance.Currencies;
import net.corda.finance.contracts.asset.Cash;
import net.corda.testing.node.MockServices;
import net.corda.samples.obligation.TestUtils;
import net.corda.samples.obligation.states.IOUState;
import org.junit.Test;
import java.util.Arrays;
import java.util.Currency;
import static net.corda.testing.node.NodeTestUtils.ledger;


/**
 * The objective here is to write some contracts code that verifies a transaction to settle an [IOUState].
 */

public class IOUSettleTests {

    public interface Commands extends CommandData {
        class DummyCommand extends TypeOnlyCommandData implements Commands{}
    }

    static private final MockServices ledgerServices = new MockServices(
            Arrays.asList("net.corda.samples.obligation.contracts")
    );


    @Test
    public void mustIncludeSettleCommand() {
        IOUState iou = new IOUState(Currencies.POUNDS(10), TestUtils.ALICE.getParty(), TestUtils.BOB.getParty());

        ledger(ledgerServices, l -> {
            l.transaction(tx -> {
                tx.input(IOUContract.IOU_CONTRACT_ID, iou);
                tx.output(IOUContract.IOU_CONTRACT_ID, iou.pay(Currencies.POUNDS(5)));
                tx.command(TestUtils.BOB.getPublicKey(), new Cash.Commands.Move());
                return tx.failsWith("Contract Verification Failed");
            });
            l.transaction(tx -> {
                tx.input(IOUContract.IOU_CONTRACT_ID, iou);
                tx.output(IOUContract.IOU_CONTRACT_ID, iou.pay(Currencies.POUNDS(5)));
                tx.command(TestUtils.BOB.getPublicKey(), new Commands.DummyCommand());
                return tx.failsWith("Contract verification failed");
            });
            l.transaction(tx -> {
                tx.input(IOUContract.IOU_CONTRACT_ID, iou);
                tx.output(IOUContract.IOU_CONTRACT_ID, iou.pay(Currencies.POUNDS(5)));
                tx.command(TestUtils.BOB.getPublicKey(), new Cash.Commands.Move());
                tx.command(Arrays.asList(TestUtils.ALICE.getPublicKey(), TestUtils.BOB.getPublicKey()), new IOUContract.Commands.Settle());
                return tx.verifies();
            });
            return null;
        });
    }



    @Test
    public void mustHaveOneInputIOU() {
        IOUState iou = new IOUState(Currencies.POUNDS(10), TestUtils.ALICE.getParty(), TestUtils.BOB.getParty());
        IOUState iouOne = new IOUState(Currencies.POUNDS(10), TestUtils.ALICE.getParty(), TestUtils.BOB.getParty());

        ledger(ledgerServices, l -> {
            l.transaction(tx -> {
                //No input
                tx.command(Arrays.asList(TestUtils.ALICE.getPublicKey(), TestUtils.BOB.getPublicKey()), new IOUContract.Commands.Settle());
                tx.output(IOUContract.IOU_CONTRACT_ID, iou.pay(Currencies.POUNDS(5)));
                tx.failsWith("One input IOU should be consumed when settling an IOU.");
                return null;
            });
            l.transaction(tx -> {
                //One input and one output
                tx.input(IOUContract.IOU_CONTRACT_ID, iou);
                tx.command(Arrays.asList(TestUtils.ALICE.getPublicKey(), TestUtils.BOB.getPublicKey()), new IOUContract.Commands.Settle());
                tx.output(IOUContract.IOU_CONTRACT_ID, iou.pay(Currencies.POUNDS(5)));
                tx.command(TestUtils.BOB.getPublicKey(), new Cash.Commands.Move());
                tx.verifies();
                return null;
            });
            l.transaction(tx -> {
                //One input and no output
                tx.input(IOUContract.IOU_CONTRACT_ID, iouOne);
                tx.command(Arrays.asList(TestUtils.ALICE.getPublicKey(), TestUtils.BOB.getPublicKey()), new IOUContract.Commands.Settle());
                tx.verifies();
                return null;
            });
            return  null;
        });

    }


    @Test
    public void mustNotHaveMoreThanOneOutputIOU() {
        IOUState iou = new IOUState(Currencies.DOLLARS(10), TestUtils.ALICE.getParty(), TestUtils.BOB.getParty());
        ledger(ledgerServices, l -> {
            l.transaction(tx -> {
                tx.input(IOUContract.IOU_CONTRACT_ID, iou);
                tx.command(Arrays.asList(TestUtils.BOB.getPublicKey(), TestUtils.ALICE.getPublicKey()), new IOUContract.Commands.Settle());
                tx.verifies();
                return null;
            });
            l.transaction(tx -> {
                tx.input(IOUContract.IOU_CONTRACT_ID, iou);
                tx.output(IOUContract.IOU_CONTRACT_ID, iou.pay(Currencies.DOLLARS(5)));
                tx.output(IOUContract.IOU_CONTRACT_ID,iou.pay(Currencies.DOLLARS(6)));
                tx.command(Arrays.asList(TestUtils.BOB.getPublicKey(), TestUtils.ALICE.getPublicKey()), new IOUContract.Commands.Settle());
                tx.failsWith("No more than one output IOU should be created");
                return null;
            });
            return null;
        });
    }


    @Test
    public void onlyPaidPropertyMayChange() {
        IOUState iou = new IOUState(Currencies.DOLLARS(10), TestUtils.ALICE.getParty(), TestUtils.BOB.getParty());

        ledger(ledgerServices, l -> {
            l.transaction(tx -> {
                tx.input(IOUContract.IOU_CONTRACT_ID, iou);
                IOUState iouCopy = iou.copy(iou.getAmount(), iou.getLender(), TestUtils.CHARLIE.getParty(), iou.getPaid()).pay(Currencies.DOLLARS(5));
                tx.output(IOUContract.IOU_CONTRACT_ID, iouCopy);
                tx.command(Arrays.asList(TestUtils.BOB.getPublicKey(), TestUtils.ALICE.getPublicKey()), new IOUContract.Commands.Settle());
                tx.failsWith("Only the paid amount can change during part settlement.");
                return null;
            });

            l.transaction(tx -> {
                tx.input(IOUContract.IOU_CONTRACT_ID, iou);
                IOUState iouCopy = iou.copy(Currencies.DOLLARS(0), iou.getLender(), TestUtils.CHARLIE.getParty(), iou.getPaid()).pay(Currencies.DOLLARS(5));
                tx.output(IOUContract.IOU_CONTRACT_ID, iouCopy);
                tx.command(Arrays.asList(TestUtils.BOB.getPublicKey(), TestUtils.ALICE.getPublicKey()), new IOUContract.Commands.Settle());
                tx.failsWith("Only the paid amount can change during part settlement.");
                return null;
            });

            l.transaction(tx -> {
                tx.input(IOUContract.IOU_CONTRACT_ID, iou);
                IOUState iouCopy = iou.copy(iou.getAmount(), TestUtils.CHARLIE.getParty(), iou.getBorrower(), iou.getPaid()).pay(Currencies.DOLLARS(5));
                tx.output(IOUContract.IOU_CONTRACT_ID, iouCopy);
                tx.command(Arrays.asList(TestUtils.BOB.getPublicKey(), TestUtils.ALICE.getPublicKey()), new IOUContract.Commands.Settle());
                tx.failsWith("Only the paid amount can change during part settlement.");
                return null;
            });

            l.transaction(tx -> {
                tx.input(IOUContract.IOU_CONTRACT_ID, iou);
                IOUState iouCopy = iou.copy(iou.getAmount(), iou.getLender(), iou.getBorrower(), iou.getPaid()).pay(Currencies.DOLLARS(5));
                tx.output(IOUContract.IOU_CONTRACT_ID, iouCopy);
                tx.command(Arrays.asList(TestUtils.BOB.getPublicKey(), TestUtils.ALICE.getPublicKey()), new IOUContract.Commands.Settle());
                tx.verifies();
                return null;
            });

            return null;
        });

    }

    @Test
    public void paidPropertyMustIncreaseForPartSettlement() {
       IOUState iou = new IOUState(Currencies.DOLLARS(10), TestUtils.ALICE.getParty(), TestUtils.BOB.getParty());
        ledger(ledgerServices, l -> {
            l.transaction(tx -> {
                tx.input(IOUContract.IOU_CONTRACT_ID, iou);
                IOUState iouCopy = iou.copy(iou.getAmount(), iou.getLender(), TestUtils.BOB.getParty(), iou.getPaid()).pay(Currencies.DOLLARS(5));
                tx.output(IOUContract.IOU_CONTRACT_ID, iouCopy);
                tx.command(Arrays.asList(TestUtils.BOB.getPublicKey(), TestUtils.ALICE.getPublicKey()), new IOUContract.Commands.Settle());
                tx.verifies();
                return null;
            });
            l.transaction(tx -> {
                tx.input(IOUContract.IOU_CONTRACT_ID, iou);
                IOUState iouCopy = iou.copy(iou.getAmount(), iou.getLender(), iou.getBorrower(), iou.getPaid()).pay(Currencies.DOLLARS(5));
                tx.output(IOUContract.IOU_CONTRACT_ID, iouCopy);
                tx.command(Arrays.asList(TestUtils.BOB.getPublicKey(), TestUtils.ALICE.getPublicKey()), new IOUContract.Commands.Settle());
                tx.verifies();
                l.transaction(tx2 ->{
                    tx2.input(IOUContract.IOU_CONTRACT_ID, iouCopy);
                    IOUState iouCopyOfCopy = iouCopy.copy(iou.getAmount(), iou.getLender(), iou.getBorrower(), Currencies.DOLLARS(4));
                    tx2.output(IOUContract.IOU_CONTRACT_ID, iouCopyOfCopy);
                    tx2.command(Arrays.asList(TestUtils.BOB.getPublicKey(), TestUtils.ALICE.getPublicKey()), new IOUContract.Commands.Settle());
                    tx2.failsWith("The paid amount must increase in case of part settlement of the IOU.");
                  return null;
                });
                return null;
            });
            return null;

    });
    }



    @Test
    public void mustBeSignedByAllParticipants() {
        IOUState iou = new IOUState(Currencies.DOLLARS(10), TestUtils.ALICE.getParty(), TestUtils.BOB.getParty());

        ledger(ledgerServices, l -> {
            l.transaction(tx -> {

                tx.input(IOUContract.IOU_CONTRACT_ID, iou);
                tx.output(IOUContract.IOU_CONTRACT_ID, iou.pay(Currencies.DOLLARS(5)));
                tx.command(Arrays.asList(TestUtils.ALICE.getPublicKey(), TestUtils.CHARLIE.getPublicKey()), new IOUContract.Commands.Settle());
                tx.failsWith("Both lender and borrower must sign IOU settle transaction.");
                return null;
            });
            l.transaction(tx -> {
                tx.input(IOUContract.IOU_CONTRACT_ID, iou);
                tx.output(IOUContract.IOU_CONTRACT_ID, iou.pay(Currencies.DOLLARS(5)));
                tx.command(TestUtils.BOB.getPublicKey(), new IOUContract.Commands.Settle());
                tx.failsWith("Both lender and borrower must sign IOU settle transaction.");
                return null;
            });
            l.transaction(tx -> {
                tx.input(IOUContract.IOU_CONTRACT_ID, iou);
                tx.output(IOUContract.IOU_CONTRACT_ID, iou.pay(Currencies.DOLLARS(5)));
                tx.command(Arrays.asList(TestUtils.BOB.getPublicKey(), TestUtils.ALICE.getPublicKey()), new IOUContract.Commands.Settle());
                tx.verifies();
                return null;
            });
            return null;
        });

    }
}
