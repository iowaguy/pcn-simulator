# Why are you reading this?
- What are interledger payments and how it works.

# Problem
- Two modes of interledger payments (on-chain) across a list of ledgers using connectors; Atomic - using notaries and Universal mode.

# Introduction
- Facilitate payment between different payment systems by creating a "connection" between these different payment systems(or ledgers). Connection is between two accounts in these payment systems.
- Requires no global co-ordinating system.
- Any ledger that supports escrow transfers can integrate with this protocol. Escrow is needed for secure payments, to prevent someone from stealing without holding up their end of the deal.
- Current "connectors" (aka entity that facilitates interledger payment) need to be trusted by some legal contract. This is cumbersome and not easy for everyone to become a connector, hence market for connectors is uncompetitive.

# Features:
- Secure payments.
- Sender is guaranteed reciept (cryptographically signed).
- Two modes of payment executing: Atomic/Universal.

# Definitions:
- Byzantine: Technical failure, harm other parties or impede protocol.
- Altruistic: Always follows protocol.
- Rational: Self-interested. May or maynot follow to maximise benefits.

# Byzantine model:
- Considers Byzantine and Rational parties.
- Rational actors require incentives to participate - in the form of fees.
- Byzantine ledgers are not considered. Responsibility of the participants to choose Byzantine fault tolerant ledgers.
- Connectors will only do things that benefit them.
- Any or all participants may try to benefit themselves.

# Summary:

## Universal Mode:
- Sender -> l1 -> l2 ...... -> ln -> reciever.
- At each step, each connector puts funds to be sent to the next connector in an escrow in the next connectors ledger.
- Finally, the receiver receives the payment in their ledger at which point a cryptographically signed reciept is sent back.
- At each point, whenever a ledger receives this receipt, it unlocks the fund and gives it to the connector. This finally trickles back to the sender, at which point the first connector gets their payment and the transaction is complete.

### Receipt Privacy
- To protect the recipient from losing anything as they sign the receipt, Receipt Priavacy is considered.
- Basically a ledger should only reveal the "execute" and "receipt signature" after the transaction is completed.


## Atomic Mode:
- Works like 2PC, instead of a single co-ordinator, we have a set of notaries.
- Every party puts money in the escrow to be sent to the connector ahead of them.
- The funds are only released by the ledgers when they receive a "Execute" from the notaries and a "PaymentReceipt" from the receiver.

### Selecting Notaries
- First step is to find a set of notaries such that they are tolerant to f Byzantine faults.
- To do this, the sender first sets a threadhold Fmax as its max fault tolerant threshold.
- Next, all the participants privide their set of trusted notaries such that its tolerant to f faults.
- The sender now finds a subset from all the participants notaries tolerant to f faults.
- If the above is not possible, then this transaction is not possible and the universal mode must be selected.

### Proposal
- Sender sends a proposal along the path until the receiver.
- Each connector verifies the payment info (fees and all that) and accepts the proposal.
- On receiving a green light from all connectors, the sender proceeds to the preparation phase.

### Prepare
- In this phase, the sender first transfers funds for the first connector into the escrow.
- On seeing this, the first connector transfers the fund for the second connector and this continues until the receiver.

### Execute
- Now that the funds are escrowed. The receiver signs a Received signature and sends them to the escrows. THe receiver is comfortable doing this because they can see that the funds have been escrowed.
- The notaries will now run a consensus protocol to see if payment should execute.
- If they agree, then send a "receipt signature" and "execute signature" to all participants.
- Each participant takes these are and submits to the previous ledger to receive the funds from the escrow.
- ON the other hand if the notaries decide that the payment receipt was not received on time and that it failed, they send an "abort" message. At this point, each connector submits that to reclaim their respective escrowed funds.

# Questions
- How does routing work? Can we design a protocol where the ledgers automatically pick the next ledger?
- In universal mode, what if one of the connectors go down timing out the others? This way sender does not need to pay and recipient gets money as well. Sender can attack any of the connectors to make it go down.
- How is Liquidity starvation prevented in Atomic mode? How does connector set fee for setup?
- Picking timeouts in universal mode.
- How should exchange rates be handled? How do the connectors calculate the fees?




