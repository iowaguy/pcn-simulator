# Work Plan
## Simulator Overview:
- Algorithms Supported
## Simulation Data:
- Understand how simuation data is constructed from the data set provided. (Ripple data from Jan 2013 - Aug 2016).
- What are the final list of files used in the evaluation and what are each of them used for?
## Simulation Results:
- Running the simulation outputs a set of files, what do each of those files have/mean?
- How are the results evaluated? Graphs plotted etc
## Paper evaluation verification:
- After understanding the data and the results. Try reproducing the results from the paper.
- Run the simulation on an updated data set (Ripple data from 2016 - Present). This is interesting because Ripple had a transaction surge in 2017.
* What is the expected result?
* What is the actual result? was SpeedyMurmurs still more performant than Silent Whispers in the presence of high transactional load which probably resulted in frequent tree rebalancing.


# Algorithms Supported
- 0:  LM-MUL-PER - SilentWhispers
- 1:  LM-RAND-PER
- 2:  LM-MUL-OND
- 3:  LM-RAND-OND
- 4:  GE-MUL-PER
- 5:  GE-RAND-PER
- 6:  GE-MUL-OND
- 7:  GE-RAND-OND - SpeedyMurmurs
- 8:  ONLY-MUL-PER
- 9:  ONLY-RAND-OND
- 10: max flow

## Routing algorithm
* LM: Landmark
* GE: Greedy Embedding
* TO: Tree only

## Stabilization Method
* PER: Periodic
* OND: On Demand

## Assignment of credit on paths
* MUL : Multi party computation
* RAND: Random computation

## Landmark Selection
* HD: Highest Degree
* RL: Random Landmark


## Data Set

### Transaction Data: (ripple-transactions-jan-2013-aug-2016.txt)
- Format: tx_hash, sdr, rev, currency, amount1, amount2, ledger, tag1, tag2, crawl_id, unix_timestamp
- Above format is then converted to tx_hash, sdr, rcv, USD_amount, unix_timestamp in (transactions-in-USD-jan-2013-aug-2016.txt) using known currencies and converting them to USD (based on rate on Nov 9th).
Moreover, total amount = amount1 * amount2/10^8

### Trust Lines: (raw-trust-lines-2016-nov-7.txt):
- Ripple network allows IOU's. Alice can pay Bob 10$ as an IOU. Bob can then pay Claire 5$ using Alice's IOU. Alice now has to pay 5$ to Bob and 5$ to Claire when they redeem their IOU's. This feature requires both Claire and Bob to trust Alice, which is tougher in the real world. "Trust Lines" are used to create a chain of such trusted intermediatories.
- Explanation links: https://medium.com/@AlexCarrithers/xrp-vs-ious-on-ripple-what-are-they-and-which-are-banks-using-257023fc578e, https://developers.ripple.com/issued-currencies-overview.html
- Format: account(address), lines: [], status, type
- Lines Format: account(address), balance, currency, limit(upper_bound), limit_peer(lower_bound), no_ripple, quality_in, quality_out

- These are now converted as: src, dest, lower_bound, balance, currency, upper_bound
in (complete-parsed-trust-lines-2016-nov-7.txt).

- Finally, converting each currency to its equivalent USD amount (based on rate on Nov 9th), format: src, dest, lower_bound, balance, upper_bound in (all-in-USD-trust-lines-2016-nov-7.txt).

### Scripts Used
- parse_trust_lines.py: parses the crawled ripple graph in file (ripple-graph-jan-2013.txt)parsed content in (all-in-USD-trust-lines-2013-jan.txt).
- parse_trust_set_transactions.py: parses the create link transactions in file (trust-set-transactions.txt), parsed content in (links-created-in-USD-jan-2013-dec-2016.txt).

## Data Set Construction:
- ParseFilesToGTNA.java converts the above sets into the required format for GTNA.
*More details required here*


# How are results evaluated and plotted
## Metrics used
- Success Ratio: fraction of successful transations (higher better).
- Delay        : longest chain of messages (lower better).
- Path Length  : length of discovered path between sender and receiver (lower better).
- Stabilization: number of messages sent per epoch to stabilize trees (lower better).







# Evaluation Ideas
- Run evaluation on updated Ripple Data set - after 2016 to present date and compare results with results from the currently used data set (jan 2013 - oct 2016). This is interesting because there was a major increase in ripple transactions from 2017 - early 2018.

# Idea
- Evaluate conditions where Silent Whispers is better and when Speedy Murmurs is and ford fulkerson, suggest hybrid based on network state.
- Rayo and Fulgor handles privacy and concurrency but not path selection, Speedy Murmurs handles that, combine them.

# Read
- Flare
- Canal
- Priv Pay
- Rayo and Fulgor
