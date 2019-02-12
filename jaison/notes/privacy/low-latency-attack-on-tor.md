# Why are you reading this paper?
* What is a low-latency attack?
* Problems with tor routing.

# Problem
* Routing performance optimisations result in vulnerabilities in entry guards. Exploring these and discussions on how to prevent them.

# Attacker
* low-resource OR
* Controlling guard and exit = compromised.
* more than 1.
* Needs to collaborate.

# Attacks
- New Clients: Advertise high bandwidth and get selected on entry and exit. If not, disrupt path and force reselection until this happens. Once entry and exit compromised, simple timing attack.
- Compromise existing clients: Dos entry guards and force reselection.
- Improve performance of selected clients: Deny to others and only service certain clients.
- Displace honest entry guards: Raise median value by advertising really high bandwidth and uptime (Sybil attack since single IP can have 2^16-1 ORs)

# Solutions
- OPs should have ORs running locally and route through that.
- Advertised values should be monitored or verified somehow.
- Reputation system.
- Sybil Attacks: TOR previously allowed as many ORS from single IP, now it is restricted to 3. Circuits cannot have ORs from the same address space.


# Questions
- Section 3: Last paragraph: Necessary, next hop of entry node is previous hop of exit node. Does that mean that it can only be done for 3 ORs?
- How does watermarking work? Section 5 last paragraph.
