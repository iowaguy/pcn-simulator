# Problem
- Internet routing is based on trust and prone to byzantine failures.

# Solution
- Use overlay routing.

# GOAL
- Complete intrusion-tolerant network solution with a resilient networking architecture (IP level) with an intrusion tolerant overlay.
- Extend this overlay to meet the needs of targeted high value applications.


# Network Model:
- Intrusion-tolerant network:
    * intrusion-tolerant messaging protocols running on top of resilient networking architecture which uses an overlay network to make use of several IP networks (ISP backbones).
- Overlay network:
    * overlay node and logical edges (links).
    * overlay nodes have a set of neighbors.
- Communication is authenticated using a PKI
    * System admin and each node in the overlay network has a pub/private key and knows of all other public keys.
- Overlay network topology is known by all nodes and changes are made by sys admin.

# Considered Attacks:
- Provide guarantees in the presence of compromises.
- Any node that does not follow the protocol faithfully is *A compromised Node*.
-*A failed edge* is an edge that fails to pass messages freely. Whatever the reason maybe.


# Issues
- Why require atomic clocks? Minor skews can be tolerated using internal clocks like E2E timers.
- What happens when high priority messages are dropped?