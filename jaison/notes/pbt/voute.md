# Why are you reading this?
- To understand how routing based on prefix embedding works.
- To understand how privacy is maintained in this type of routing.

# What is the problem presented in the paper?
- Greedy embedding based routing does not provide anonymity and are prone to denial-of-service attacks.
- Solution is to provide an effecient privacy preserving greedy embeddings based routing scheme which uses anonymous return addresses and also effectively mitigates powerful denial-of-service attacks.

# Evaluation
- Performance analysis
- Security analysis
- Simulation study

# Questions
- Why/how are greedy embedding based routing prone to DOS attacks.

# To read
- Attacks on TOR: blog.torproject.org/category/tags/attacks
- Greedy network embeddings: Isometric embedding protocol and geographic routing using hyperbolic space
	- General idea: 
		- Construct spanning tree of the network.
		- Assign co-ordinates to each node based on its position.
		- To contact a node, you require its coordinate.
	- Problem: Arbitrary participants can reconstruct the graph based on the coordinates revealed by the proto				  col and then identify participants. Hence, anonymity is broken.
- What is pollution and local eclipse attack?

