# Why are you reading the paper?
- Exploring attacks on TOR
- What are RAPTOR attacks?
- How is TOR suceptible to routing attacks?

# Problem
- Exploring active attacks on TOR: AS-level adversaries who are NOT in the path between entry and exit can observe TOR traffic by launching BGP routing attacks.
- Designing system to protect TOR from it.

# Terms
- Resilience: Source AS v is resilient if it is not deceived by a false origin AS a as a true origin AS t and henc sends traffic to true origin AS t.
* origin-source-attacker resilience: p of v being resilient to a and sending to t.
* origin-source resilience: p of v being resilient if other ASes perform prefix attack on t.
* origin resilience: avg p of all sources being resilient to other ASes performing perfix attack on t.

# Attacks
- BGP hijacking: When an AS broadcasts same IP as another AS.
- BGP interception: Change prefix to a higher value so that BGP routing takes place through them.

# Solution
- Measurement: Study on TOR network to see the effectiveness of these attacks.
- Guard Relay Selection: Proactive approach against BGP attacks.
* Include a new metric called "resilience" in the guard router selection algorithm, which takes into account how resilient the client AS and also the bandwidth of the router.
* W(i) = a * R(i) + (1-a) * B(i).
- BGP Monitoring System: Reactive approach against BGP attacks.
* Live monitor all routes to TOR routers.
* Increase routing transparency.
* Anomaly detection on routing data.
-- Origin AS check
-- Frequency Analytics
-- Time Analytics

# Code
https://www.github.com/inspire-group/Counter-Raptor-Tor-Client

# Personal Take
- Never even considered TCP level attacks to de-anonymise users.
- Overlooked the basic concept that onion routers, even though they encrypt data, still need to route packets using TCP over ASes.


