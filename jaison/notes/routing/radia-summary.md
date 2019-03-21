Networking Protocols with Byzantine Robustness
==============================================

What is the purpose of the thesis?
---------------------------------
* Present a design of a computer network which is resistant to malfunctions such as faults and intentional incorrect behaviour.
* Focuses ONLY on the "Networking Layer" - nodes co-operate and transfer packets from a source to destination.


Byzantine Generals Problem:
---------------------------
* How do you design a system where a malicuous node can corrupt decisions by lying.
* Conditions of success:
    * Termination: All nodes eventually reach a decision.
    * Agreement: All non-faulty nodes decide on the same thing.
    * Validity: If general is non faulty, all non-faulty nodes decides on generals decision.
* Results:
    * No solution if non-faulty nodes are more than 1/3 of the total nodes.
    * No solution in asynchronous settings.
    * To tolerate t faults, atleast t rounds are required for any deterministic algo.

Why can Byzantine Generals problem be solved in a "Networking Layer" setting?
----------------------------------------------------------------------------
* Simultaneity: Network layer can work even if all nodes dont decide on something simultaneuosly.
* Agreement: Agreement between non faulty nodes are not necessary.
* Termination: Network layer never terminates.
* Cryptography: Use of cryptography makes the problem tractable.



Public Key Cryptography:
-----------------------
* A person X has two keys, public key (E) and private key (D).
* Encryption: Any data TO X can be "encrypted" by using X's public key (E). To "decrypt" you need X's private key (D) - which only X knows.
* Digital Signatures: X can "Sign" some data to be sent using its Private Key (D) and others can verify if the data is indeed from X by verifying the signature using X's public key (E).



OSI Reference model:
-------------------
The internet is designed into various levels:
- 1.Physical
- 2.Data Link
- 3.Network
- 4.Transport
- 5-7.Session, Presentation, User

Network Layer:
-------------
* Contains a mesh network of nodes, so that a source node can communicate with a destination node using the intermediate nodes to transfer data.
* Types of service:
    - Connection oriented
    - Datagram
* Degree of self-configuration:
    - Manual maintenance of routes: Route to destination from soruce are calculated and manually maintained at each source node.
    - Automatic: Assimilate new nodes as they are hooked in and automatically reconfigure around failed component.

Two Popular Distributed Routing Algorithm:
-----------------------------------------
* Distance Vector (Bellman Ford Algorithm)
    - Routing computation is done in a distributed fashion.
    - Each source maintains vector to each destination.
* Link state (OSPF Protocol)
    - Local topological info is broadcast to the network by each node, i.e each node has a database of the complete topology of the network.

Levels of Robustness:
--------------------
* Simple: Genuine faults are detected and manual intervention to fix issues.
* Self-stabilising: When malfunctioning nodes are removed, network continues working. No guarantees when malfunctioning node exists in the network.
* Byzantine Detection: Detects a byzantine node and then manual intervention to remove.
* Byzantine robustness: Even if byzantine nodes exits in the network, network will function correctly.


#### SYSTEM DETAILS:
* Layer: Network Layer
* Type of service: Datagram
* Degree of self configuration: Automatic
* Assumptions and model:
    - As long as there exists a path between source and node, each packet will be delivered to destination from source with reasonable probabilty, i.e no reliability for message delivery and no order is maintained either. This is the responsibility of the transport layer.
    - Conversations are not private.
    - Does not certify packets it delivers, i.e destination might receive junk like damaged packets (in addition to undamaged), packets which claim to be from source A but are not.
* Network Protocol (Uses a modified Link State Routing algorithm)
        - Discovers and disseminates topological information about the network.
        - Calculates routes based on disseminated topological information.
        - Forwards data packets based on calculated routes.
    - Network protocol with Byzantine robustness with some level of Byzantine detection(No more than t faults).
    - If a faulty node has been fixed, it should be able to participate in network.


Issues
======
- Links are assumed to be point-point (not shared).
    * with shared transport medium (wireless, ethernet) how do you perform byzantine detection?
- Is there a experimental system built on this? Is it a good idea to try it out?
- Since each key/id pair is considered seperate storage costs are high.
    * o(t^2n^2) - each t trusted node gives n links -> tn.
- each tn link will have tn other links -> t^2n^2.
    * Solution: using subnets
- Trusted node services for handling changes to public keys, adding/removing nodes etc ... not that great.
- Nodes need to track their link reliability themselves regularly, to ensure that a byzantine node is not acting as a dumb relay and if it is then detect and declare the link useless.


