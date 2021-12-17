# Obligation Cordap

This Cordapp is the complete implementation of our signature IOU (I-owe-you) demonstration.This sample has been created
for learning and training and is not been tested for production.

## Concepts

An IOU is a loan agreement between a borrower, and a lender with the borrower saying "I OWE YOU" an amount.You have to
have the original concept of the debt itself - the IOU. Then the ability to transfer the IOU from one lender to another
and finally the ability to settle up. Similar logic could be utilized to trade assets on Corda. Given this is intended
to implement an IOU, our cordapp consists of three flows `issue`
, `transfer` and `settle` flows.

### Flows

The first flows are the ones that issue the loan agreement.You can find the IOU issurance in `IOUIssueFlow.java`.

The next flow is the one that transfers ownership of that asset over to another party. That can be found
in `IOUTransferFlow.java`.

Finally, once we have the ability to transfer assets, we just need to settle up. That functionality can be found here
in `IOUSettleFlow.java`

## Usage

### Running the CorDapp

1. Open a terminal and go to the project root directory and type: (to deploy the nodes using bootstrapper)

```
./gradlew clean deployNodes
```

Then type: (to run the nodes)

```
./build/nodes/runnodes
```

2. To issue an IOU with ParticipantA as borrower and ParticipantB as lender. Run the below command at ParticipantA 
   terminal.

```
flow start IOUIssueFlow$InitiatorFlow amount: 10, lender: "O=ParticipantB,L=New York,C=US"
```

3. Run valut query and check which nodes have the created state in their vault.

```
run vaultQuery contractStateType: net.corda.samples.obligation.states.IOUState
```

4. Above command will give you the `linearId` of the state. Use it to start transfer flow at ParticipantB.

```
flow start IOUTransferFlow$InitiatorFlow stateLinearId: "5afa8813-78d1-4015-b86d-2c090fb207f3", newLender: "O=ParticipantC,L=Paris,C=FR"
```

Now you could query the vault of ParticipantC to find the IOU state.

5. Settle the flow in-part/ full from ParticipantA

```
flow start IOUSettleFlow$InitiatorFlow stateLinearId: "b92072bd-2b5a-40be-9b98-ec73e2a83867", pay_amount: 5
```

or

```
flow start IOUSettleFlow$InitiatorFlow stateLinearId: "b92072bd-2b5a-40be-9b98-ec73e2a83867", pay_amount: 10
```