# Why am I reading this?
- To understand what packet-switched networks are.
- The problem mentioned is interesting.

# What problem are they solving?
- In "ciruit-switched" payment channel networks (like SpeedyMurmurs), large transactions are either difficult to transact or they block smaller transactions by depleting funds on one side of a payment channel.

# Areas of research in payment-channel-networks
- Payment channels lock funds that cannot be used for anything else, therefore high transaction throughput with a small amount of capital.
- Incentive participants.
- Privacy of user transactions (value and sender/receiver privacy).

# Ideas
- Congestion control for payment-channel-networks: Congestion control algos need to be deadline-aware since transations are timed.
- Explore imbalance-aware congestion control algorithm
