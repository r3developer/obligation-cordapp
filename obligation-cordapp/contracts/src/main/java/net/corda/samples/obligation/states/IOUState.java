package net.corda.samples.obligation.states;

import net.corda.core.contracts.*;
import net.corda.core.identity.Party;
import net.corda.core.identity.AbstractParty;

import java.util.*;

import com.google.common.collect.ImmutableList;
import net.corda.core.serialization.ConstructorForDeserialization;
import net.corda.samples.obligation.contracts.IOUContract;
import org.jetbrains.annotations.NotNull;

/**
 * The IOU State object, with the following properties:
 * - [amount] The amount owed by the [borrower] to the [lender]
 * - [lender] The lending party.
 * - [borrower] The borrowing party.
 * - [contracts] Holds a reference to the [IOUContract]
 * - [paid] Records how much of the [amount] has been paid.
 * - [linearId] A unique id shared by all LinearState states representing the same agreement throughout history within
 * the vaults of all parties. Verify methods should check that one input and one output share the id in a transaction,
 * except at issuance/termination.
 */

@BelongsToContract(IOUContract.class)
public class IOUState implements ContractState, LinearState {

    private final int amount;
    private final Party lender;
    private final Party borrower;
    private final int paid;
    private final UniqueIdentifier linearId;

    @ConstructorForDeserialization
    public IOUState(@NotNull final int amount, @NotNull final Party lender, @NotNull final Party borrower, @NotNull final int paid, @NotNull final UniqueIdentifier linearId) {
        this.amount = amount;
        this.lender = lender;
        this.borrower = borrower;
        this.paid = paid;
        this.linearId = linearId;
    }

    public IOUState(@NotNull final int amount, @NotNull final Party lender, @NotNull final Party borrower) {
        this(amount, lender, borrower, 0, new UniqueIdentifier());
    }


    public int getAmount() {
        return amount;
    }

    @NotNull
    public Party getLender() {
        return lender;
    }

    @NotNull
    public Party getBorrower() {
        return borrower;
    }

    public int getPaid() {
        return paid;
    }

    @Override
    public UniqueIdentifier getLinearId() {
        return linearId;
    }

    /**
     * This method will return a list of the nodes which can "use" this states in a valid transaction. In this case, the
     * lender or the borrower.
     */
    @Override
    @NotNull
    public List<AbstractParty> getParticipants() {
        return ImmutableList.of(lender, borrower);
    }


}
