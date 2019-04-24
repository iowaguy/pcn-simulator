# Unidirectional payment channel:
## Setup
- Alice wants to set up a payment channel with Bob.
- Alice and Bob both have control over a multi sig address. (# how does this exactly work?)
- Alice wants to send 1 Btc to it. 
- Before sending the Btc, she gets Bob's refund signature -> Alice gets 1 Btc back in 30 days. So worst case scenario, Alice cannot use money for 30 (or n) days. Bob signs this refund transaction and sends it to Alice. (# how does this exactly work?)
- Alice receives this transaction, she either signs it right away or later.
- Once she has this, Alice sends 1 btc from her address to the Alice-Bob multi sig address.

## Payments
- Alice now sends signed transactions to Bob which updates states:
	- Payment of 0.1 btc to Bob will be: Alice 0.9 btc, Bob 0.1 btc
	- Bob can now sign this transaction and submit it to the blockchain, at which point the payment channel closes or wait for future payments.
	- Alice wants to pay 0.2 btc to Bob:
		- Updated state: Bob 0.3 btc and Alice 0.7 btc -> sign and send to Bob

## Closing the channel
- Bob can close the channel by submitting the latest transaction to the blockchain network.
- Alice can also request Bob to close it, if he does not co-operate, then she has to wait for the end of the 30 day lock period. By the end of this if Bob does not submit the signed transaction from Alice that he received through the 30 day period, Alice will get all of her Btc back. But if he submits the latest state before the expiry then they will each get their respective amounts back.

# Bi-directional payment channel:
## Setup
- Same as unidirectional.

##Payments
- Alice now sends a signed transaction with a n-1 day lock-time. And for all transactions henceforth, keep updating the state with the n-1 day locktime. -> means that Bob can only broadcast this after n-1 days.
- If Bob wants to pay Alice, he overwrites the state and sends Alice a transaction with a n-2 day lock time so that she can submit this before Bob can and therefore an order for transactions is maintained.

- Alternatively, if both of them co-operate they can both sign a transaction without a locktime.

## Closing the channel
- Same as before. 

# Payment Channel Network

## Existing Payment Channels: 
Alice -> Bob 
Bob   -> Carol

Alice wants to send 0.1 btc to Carol.
=> Alice sends 0.1 to Bob, Bob sends 0.1 to Carol.

## How? Hashlocked Contracts
- Carol produces a hash H using key R.
- She sends this H to Alice.
- Alice sends a 0.1 btc transaction to Bob using the hash H, i.e this can only be unlocked by Bob if he has R.
- Bob now sends a 0.1 btc to Carol using H, Carol can only get the payment if she gives Bob R or broadcast R to the blockchain (# How does this work?).
- Carol sends R to Bob, thereby getting 0.1 from Bob. Bob uses R to unlock Alice's 0.1 btc.

### Problems
- Carol refusing to send R will lock Alice-Bob Bob-Carol funds.
- Trusting Bob

## Solution. Hashed Time-Locked contracts (HTCL)
- Alice -> Bob -> Carol -> Dave
- Alice wants to send 0.1 btc to Dave.
- Dave creates an H using R, sends it to Alice.
- Alice creates a HTLC of 0.1 btc and sends it to Bob with a 3-day nLockTime. This means that if Bob produces R within 3 days, Bob can get the 0.1 from Alice, otherwise he cannot and Alice gets a refund of 0.1 btc.
- Bob does same with Carol but with a 2-day nLockTime. 
- Carol does the same with Dave with a 1-day nLockTime.

- Dave can now pull the 0.1 from Carol by publishing R to Carol. 
- Carol does the same with Bob and then Bob to Alice.
- Thus completing the payment from Alice -> Dave.

### Byzantine Behaviour:
- If Bob refuses to co-operate with Carol and does not release the 0.1 after she released it to Dave, she can broadcast the whole transaction chain on the blockchain and force the release of 0.1 from Bob to herself (Carol).
- Now Bob sees R on the blockchain and can pull the funds from Alice (even though he try to mess up the transaction). 
- If Bob is not attentive and does not pull the funds from Alice, then Alice can pull her funds back after the 3 day lock period.


