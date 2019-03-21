# Reading Intention
- What are Path-Based Networks?
- Understand what the paper is talking about
- Find problems (if any) with routing, privacy or security(confidentiality, integrity & availability).

# Problem the paper is solving
- Provide a routing algorithm for off-chain PBT networks which is more effecient than the current state-of-the-art (Silent Whispers) and also privacy preserving. i.e extend VOUTE to credit networks.
- Also provide analysis based on simulation study and quantifies advantage of said routing algorithm.

# Summary

## Introduction
- Blockchains have scalability issues.
- Solution to slow on-chain transactions is to have off-chain transactions like Bitcoins lightening network or Ethereums Raiden network.
- Current state-of-the-art does not provide effectiveness, efficiency, scalability and privacy. They fall short on something.
- SilentWhispers is the closest with regards to privacy but its routing algo is not effecient.
- Paper presents SpeedyMurmurs - based on VOUTE (privacy preserving embedding-based routing algo for messages) extended to handle weighted links.

## What are PBT networks?
- User pairs maintain weighted local links (funds for eg).
- Payments are settled off-chain and thus reduces work on blockchain.
- Payment on a link happens with the help of intermediate users who move money from them to next.
- Connections between peers are pre-defined. This does not change.

## Landmark Routing:
- There are intermediate nodes called "Landmarks" which have high connectivity.
- Each landmark performs 2 BFS. One to calculate shortest path from landmark to other nodes and another to calculate shortest path from others to itself.
- Hence, path from A -> B is route from A -> landmark (concat) landmark -> B.

- There are two types of Landmark Routing:
* landmark-centred: mentioned above.
* tree-only: landmark need not be involved, use BFS to find shortest path.

### Landmark Routing in Silent Whispers:
- Uses **landmark-centred-routing** to find path between sender and receiver.
- Payment happens in two steps: **probe** to discover weights/credit available for each link and then **payment** which completes the payment.

Probe works as follows:
- Landmarks calculate paths.
- Each pair in path shares link weights with all Landmarks.
- Landmark figures out min value Zi for each path i. As long as sumOf(Zi) >= total payment, then sender assigns Ci for each path Zi such that Ci <= Zi.

Payment completes after probe gives the values of Ci and the paths.

#### Issues with Routing in Silent Whipers:
- Landmarks periodically perform BFS; changes in links does not take effect immediately. Also this naively re-creates entire tree, whereas only changes need to be handled.
- Sometimes A and B are connected closer without the Landmark; longer paths are used. Moreover, landmark included path might fail due to low credit but A -> B may have a possible path.
- Probe operations require link's info sharing with all Landmarks.
- Concurrency is not handled. What if a link has enough for transaction A and B separately and not together. Then this might fail one of them. If B happens ONLY after A, then links which have enough are not utilised.

## Embedding-based Routing:
- Assign coordinates to all nodes.
- Disregard links and only use co-ordinates.
- Attempt links that are not present in the tree, called "shortcuts" based on closer distance (based on co-ordinates). Shortcuts may or maynot exist.
- Without shortcuts, this is the same as tree-only-routing.
- Tree needs to adapt when node or links change.

### Prefix embedding:
- Type of embedding-based routing.
- Check fig 1 in paper to understand how the co-ordinates are given. Basically, start at root -> {}, level 1, node A -> {1}, node B -> {2}. Level 2 (under node A), node C -> {1,1} and node D -> {1,2}. Basically Node A's childred get its co-ordinate as prefix.
- Distance between 2 co-ordinates is calculated as follows:
d(id(u), id(v)) = |id(u)| + |id(v)| - 2cpl(id(u),id(v)) ----- id(u) is co-ordinate of u and cpl is common prefix length.

#### Prefix-embedding in VOUTE
- Tries to do the above with privacy.
- Receiver address are hashed so that others know the common prefix length and not enough to exactly find the co-ordinate. <Not clear to me, discuss and understand>.
- Does not reconstruct tree periodically. Instead, nodes tell others their co-ordinates and offers to be parent. When new links are formed, they get these invitations from their neighbours. If a node is not part of the tree it accepts invitation and joins, otherwise it keeps the latest from each node for the future. If some link fails or ceases to exist, the node and its children choose other links as parent based on their invitations and then disseminate this info to all neighbours.

##### Limitations with using VOUTE for PBT:
- Links are unidirected and unweighted. PBT requires directed and weighted. Assumes that having a working link is enough for transfer, but in PBT weight should also be enough to perform transfer.
- Source of change is considered to be nodes leaving and joining networks. But in PBT weights are main source of change.
- Concurrency is not handled since weights are not considered.

## System Model
- Directed Graph.
- Nin(u) - set of incoming neighbours to u.
- Nout(u) - set of outgoing neighbours to u.
- L = {l1,l2....ln} = Landmarks; well-known to other users in PBT network.
- Net balance at node v => cnode(v) = sum of funds that can be transferred in - sum of funds that can be transferred out.

- Operations:
* setRoutes - initialised routing info required by each node.
* setCred - sets value of fund between u and v.
* routePay - set of tuples (Pi, Ci); Fund Ci that can be routed through path Pi.

## Byzantine model
- Does not know all links and nodes in network; cannot access routing information of non-compromised nodes; i.e no Global View of the network.
- Aims to undermine privacy.
- DOS not considered.

## Goals: Transaction Privacy
- Value privacy: Attacker cannot know transaction amount between non-compromised users; no nodes in the path are compromised.
- Sender/Receiver privacy: Attacker cannot identify sender/receiver in a transaction between uncompromised nodes; Cannot know all Nin(sender) for sender or all Nout(receiver) for receiver.

## Construction

### Assumptions:
- Every node maintains information of links with their neighbours.
- Messages/data are sent via an authenticated channel (Confidentiality).
- Set of landmarks are known to all.
- Links with no credit in either directions does not exist.

SpeedyMurmurs works on three operations:
* setRoutes
* setCred
* routePay

### setRoutes:
- Iterate over each landmark to create embeddings.
- set landmark co-ordinate to be empty.
- 2 Phases: 1st phase add all nodes with bidirectional links, 2nd Phase add all nodes with unidirectional link (this is to ensure that birectional links are added first and nodes with unidirectional links get them as parents).
- In distributed setting, phase 2 only happens after the node waits for some interval t.

### setCred:
- changes the value c for a link between two nodes.
- Change in this value may result in a co-ordinate change and that needs to be handled.

### routePay:
- discovers a set of paths from sender to receiver.
- 3 steps:
* generation of anonymous receiver address for receiver and sending it to the sender.
* split transaction value randomly on l paths (num of paths = num of landmarks).
* find path for all embeddings that can transmit value on respective path.

# Questions and ideas:
- Intro and Abstract does not talk about security, how secure is this?
- How does Atomic Swap work and how is it different from Interledger protocol?
- Landmark routing in Silent Whispers - understand how padding for sender and receiver works.
- How is privacy EXACTLY maintained in VOUTE (pg 4).
- What about security? What if there is a compromised node in the transaction path?
- Nothing is said about setup, how landmarks are selected or info about landmarks is disseminated. How are keys shared, etc.
- Simulation model did not account for concurrency - although why it should work is mentioned in paper (pg 7 & 8).
- Privacy goals are NOT entirely met: attacker if present in one of the paths can estimate c since ci < c and ci can never be -ve.

