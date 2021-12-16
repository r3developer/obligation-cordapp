package net.corda.samples.obligation.states;


import net.corda.finance.*;
import net.corda.core.contracts.*;
import net.corda.core.identity.Party;

import java.lang.reflect.Field;
import java.lang.reflect.Constructor;

import net.corda.samples.obligation.TestUtils;
import org.junit.Assert;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;


public class IOUStateTests {

    @Test
    public void hasIOUAmountFieldOfCorrectType() throws NoSuchFieldException {
        // Does the amount field exist?
        Field amountField = IOUState.class.getDeclaredField("amount");
        // Is the amount field of the correct type?
        assertTrue(amountField.getType().isAssignableFrom(int.class));
    }


    @Test
    public void hasLenderFieldOfCorrectType() throws NoSuchFieldException {
        // Does the lender field exist?
        Field lenderField = IOUState.class.getDeclaredField("lender");
        // Is the lender field of the correct type?
        assertTrue(lenderField.getType().isAssignableFrom(Party.class));
    }


    @Test
    public void hasBorrowerFieldOfCorrectType() throws NoSuchFieldException {
        // Does the borrower field exist?
        Field borrowerField = IOUState.class.getDeclaredField("borrower");
        // Is the borrower field of the correct type?
        assertTrue(borrowerField.getType().isAssignableFrom(Party.class));
    }


    @Test
    public void hasPaidFieldOfCorrectType() throws NoSuchFieldException {
        // Does the paid field exist?
        Field paidField = IOUState.class.getDeclaredField("paid");
        // Is the paid field of the correct type?
        assertTrue(paidField.getType().isAssignableFrom(int.class));
    }


    @Test
    public void lenderIsParticipant() {
        IOUState iouState = new IOUState(0, TestUtils.ALICE.getParty(), TestUtils.BOB.getParty());
        assertNotEquals(iouState.getParticipants().indexOf(TestUtils.ALICE.getParty()), -1);
    }


    @Test
    public void borrowerIsParticipant() {
        IOUState iouState = new IOUState(0, TestUtils.ALICE.getParty(), TestUtils.BOB.getParty());
        assertNotEquals(iouState.getParticipants().indexOf(TestUtils.BOB.getParty()), -1);
    }


    @Test
    public void isLinearState() {
        assert (LinearState.class.isAssignableFrom(IOUState.class));
    }


    @Test
    public void hasLinearIdFieldOfCorrectType() throws NoSuchFieldException {
        // Does the linearId field exist?
        Field linearIdField = IOUState.class.getDeclaredField("linearId");

        // Is the linearId field of the correct type?
        assertTrue(linearIdField.getType().isAssignableFrom(UniqueIdentifier.class));
    }


    @Test
    public void checkIOUStateParameterOrdering() throws NoSuchFieldException {

        List<Field> fields = Arrays.asList(IOUState.class.getDeclaredFields());

        int amountIdx = fields.indexOf(IOUState.class.getDeclaredField("amount"));
        int lenderIdx = fields.indexOf(IOUState.class.getDeclaredField("lender"));
        int borrowerIdx = fields.indexOf(IOUState.class.getDeclaredField("borrower"));
        int paidIdx = fields.indexOf(IOUState.class.getDeclaredField("paid"));
        int linearIdIdx = fields.indexOf(IOUState.class.getDeclaredField("linearId"));

        assertTrue(amountIdx < lenderIdx);
        assertTrue(lenderIdx < borrowerIdx);
        assertTrue(borrowerIdx < paidIdx);
        assertTrue(paidIdx < linearIdIdx);
    }


    @Test
    public void correctConstructorsExist() {
        // Public constructor for new states
        try {
            Constructor<IOUState> constructor = IOUState.class.getConstructor(int.class, Party.class, Party.class);
        } catch (NoSuchMethodException nsme) {
            fail("The correct public constructor does not exist!");
        }
        // Private constructor for updating states
        try {
            Constructor<IOUState> constructor = IOUState.class.getDeclaredConstructor(int.class, Party.class, Party.class, int.class, UniqueIdentifier.class);
        } catch (NoSuchMethodException nsme) {
            fail("The correct private copy constructor does not exist!");
        }
    }
}
