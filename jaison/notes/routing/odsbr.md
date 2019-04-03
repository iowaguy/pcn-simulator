## GOAL
Detect and avoid Byzantine behaviour OR bound its effect on the system. i.e Survivable routing.

## Network Model
- Bidirectional communication on all links.
- Focus is on providing secure routing protocols addressing threats to ISO/ODI network layer.
- Attacks against MAC or physical layer are ignored.
- Physical Layer assumed to use jamming-resilient techniques.

## Security Model
- Network is not open.
- Public key infrastructure provided with the help of a CA.
- Public keys - to protect route discovery phase and distribute shared keys for other phases.
    * Public keys: to authenticate nodes during route discovery.
    * Symmetric keys: subsequent phases.

## Considered Attacks
- Black hole attack: adversary drops packets while participating in routing protocol.
- Flood rushing attack: Flood the network with adversarial version so that this gets recognised and the legitimate one gets dropped.
- Byzantine Wormhole: co-ordinated nodes tunnel privately and make it seem like they're on a shorter path to get selected in route more frequently.
- Byzantine overlay network wormhole: nodes form an overlay and make it seem like they're shorter and thus get selected more often.

## Attacks not considered
- Sybil and node replication.
- Preventing traffic analysis.
- Resource consumption.
- Eavesdropping and modifiying data.
- Manipulating route discovery.

## Design
- Centered around impossibility of distinguishing between faliures and malicious behaviour. Address them both with unified framework.
- Does not use number of hops as path selection metric.
- Capture reliable and faulty behaviour with past data.
- Each node maintains weight list and updates this on faults.
- Faulty links are identified using probing techniques.

## Routing Protocol
- Route discovery in adversarial environment.
- Byzantine fault detection.
- Link weight management.

## More work
- Better shared key establishment technique to ensure perfect forwward secrecy.