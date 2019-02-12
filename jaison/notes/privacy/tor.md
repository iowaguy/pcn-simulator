# Why are you reading the paper?
- How does TOR maintain anonymity between sender and receiver in an open communication service?
- What is the system model in which TOR operates?
- Can ideas from TOR be used in credit-networks to maintain anonymity amongst transaction participants?

# Problem
- Design an anonymous communication service over TCP.
- Service should be usable to browse websites, messaging etc, ie. low-latency.
- Practical solution, ie. should scale to the level of internet and be easy to setup and incentivised for people to use.
- Flexible and simple design.
- Improve upon the limitations of the original Onion Network design.

# Not Goals
- Not peer-peer: approach has problems [ref: 23,24]
- End-end attacks: Not secure against these.
- No protocol normalisation: Does not provide ability to remove protocol features (HTTP proving client details, for eg) that leak identity.
- Not steganographic: does not conceal who is connected to the network.

# Terms
- OR or Onion Routers: Nodes participating in the system.
- OP or Onion Proxies: Users.
- Circuits: A path from client to destination via Onion Routers.

# What does each node in the system know?
- Predecessor and successor and no other nodes in the circuit.

# Attacks considered: Points in paper (Section 7)
- Passive
- Active
- Directory
- Hidden services/rendezvous points

# Design
- Basics:
* Onion proxy connects to onion routers via TLS connections.
* OP -> OR = circuit.
* Multiple TCP streams are sent through circuit.
* Communication happens via Cells, same sized encrypted packets so that they are indistinguishable.
- Setup:
* Directory servers distribute keys.
* OP figures out route, performs handshake and setsup symmetric key with every OR per circuit.
* If cell decrypts to data then send it to destination, otherwise forward to next router. This way client can specify where to exit in circuit - Leaky Pipe Circuit.
* Streams are closed and keys forgotten when node fails in circuit or hash check fails.
* Rate Limiting - Circuit level throttling: Maintain packaging and delivery window.
* Rate Limiting - Stream Level throttling: Depending on number of bytes pending to be flushed into TCP stream. Must be below some threshold.
* Rendezvous points: Clients can specify some OR as their rendezvous point that a hidden service can connect to.
* Hidden service: Services can choose an OR to be their entry for clients and broadcast that OR.
* Exit policy: ORs can opt to be exit routers or guard routers or middle routers and also have a whitelist of destination addresses that it will send data to.
* Directory Servers: Trusted servers than keep track of all ORs and their pub keys. To avoid single point of failure, there are multiple of them who must reach consensus about the state.

# Questions
- What were the limitations with the original design?
A: Covered in Overview (Page 1 and 2)
- What happens if during initial symmetric key exchange, adversary pretends to be dump forwarders and performs diffie hellmen key exchange?
- How is routing done?
- Checking integrity using SHA-1 hash, what does adding to digest do to the key?




