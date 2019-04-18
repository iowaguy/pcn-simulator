# Speedy Murmurs

## Byzantine Model
- Can control a subset of nodes in the network (inject it's own node or corrupt existing nodes).
- Does not know all links and nodes in the network.
- Cannot access routing info stored locally at non-compromised nodes.
- Aims to undermine privacy and not denial-of-service.

## Powerful Adversary
- For an attacker who knows the full topology, value privacy can still be preserved. 

## Attack Ideas:
- Adversary A and B keep sending funds forward and back to congest and exhaust network; forcing rebalancing of the spanning tree
    - Compare performance btw SW and SM.
- Adversary (S and R) keeps trying large payments between them (targetting highly connected landmarks and nodes with high balances) 
  thereby locking funds and failing honest transactions.
    - How does this affect the success ratio of normal transactions?
    - Can S and R force routing of payments through them by doing this? and thereby collect more fees?
- Analysis on how many attackers are needed and how easy it is for them to get on path for a certain % of payments to disrupt the network?

- Are there any non-verified assumptions in the model? Can adversaries lie about their connectivity? their balance 
  to attract more payments through them?
- How does one get to be a landmark? Can an adversary become a landmark? 
    - [Read silent whispers to get a better understanding of this assumption]
    - Speedy Murmurs just says that there is a list of well-known landmarks.

- Can the counter-raptor (TCP level attack) be used on PCN's to break privacy?

- Wormhole attack from: 
    - https://eprint.iacr.org/2018/472.pdf
    - https://tokyo2018.scalingbitcoin.org/files/Day1/multi-hop-locks-for-secure-privacy-preserving-and-interoperable-payment-channel-networks.pdf
    - Basically in a payment from A -> G with two colluding on-path adversaries C and E, C can steal D's fees.
        - After G releases the key to settle payment between F and G, F collects fees and forwards key to E.
        - E accepts the key and releases payment to F. 
        - Instead of forwarding key to D, E waits until timeout and intentionally fails the payment.
        - D relays this information back to C and cancels payment with C.
        - E directly contacts C and gives C the key. 
        - C now relays the key back to B but charges 2x fees (includes D's fees).
        - B then relays this back to A, completing the payment.
        - With more honest users between C and E, the more fees C can steal.

## TODO:
- Create a data set by hand for different scenarios:
    - [ ] Run Dynamic sim for a VERY small data set (runtime < 1min) and compare/reproduce graph. 