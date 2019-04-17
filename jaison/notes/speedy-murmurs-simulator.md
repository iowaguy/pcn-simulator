# Work Plan
**Simulator Overview**
- [x] [What are the Algorithms Supported?](#algorithms-supported)
- [ ] Understand routing code for SpeedyMurmurs 
- [ ] Understand routing code for SilentWhispers
- [ ] Get the spider network simulator to compile and work
- [ ] Understand routing code for SpiderNetwork

**Simulation Data**
- [x] [Understand how simuation data is constructed from the data set provided. (Ripple data from Jan 2013 - Aug 2016)](#data-set)
- [x] What are the [final list of files used in the evaluation](#final-list-of-files-used-in-simulation) and what are each of them used for?


**Simulation Results**
- [x] [Running the simulation outputs a set of files, what do each of those files have/mean?](#simuation-output)
- [x] [How are the results evaluated? Graphs plotted](#graphs-plotted)

**Paper evaluation verification**
- [x] After understanding the data and the results. Try reproducing the results from the paper.
- [ ] Look for other data sets that can be used in the simulator.

**Exploring Byzantine behaviour**
- [ ] Note on the attacker model assumed. List assumptions.
- [ ] Recreate the attacker model assumed in paper and observe behaviour.
- [ ] List of attacks read until now (from the other papers).
- [ ] Come up with a list of additional attacks that can be performed. Run them by Cristina and Ben.
- [ ] What happens when a few transactions are missing or repeated in the data set? How do speedy murmurs and silent whispers behave?
- [ ] Understand how EXACTLY the algorithm is implemented and run these attacks in the simulator.
- [ ] Will it be possible to modularize/improve the simulator to simulate byzantine behaviour with a simple config file?

**Papers to read**
- [ ] Voute
- [ ] Flare
- [ ] Canal
- [ ] Priv Pay
- [ ] Rayo and Fulgor

**TODO later**
- [ ] Run the simulation on an updated data set (Ripple data from 2016 - Present). This is interesting because Ripple had a transaction surge in 2017.
    * What is the expected result?
    * What is the actual result? was SpeedyMurmurs still more performant than Silent Whispers in the presence of high transactional load which probably resulted in frequent tree rebalancing.


## Ideas
- Evaluate conditions where Silent Whispers is better and when Speedy Murmurs is and ford fulkerson, suggest hybrid based on network state.
- Rayo and Fulgor handles privacy and concurrency but not path selection, Speedy Murmurs handles that, combine them.
- The network simulator is not that great. Work on a specific simulator for Payment Channels?


# Work Progress

## Algorithms Supported
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

#### Routing algorithm
* LM: Landmark
* GE: Greedy Embedding
* TO: Tree only

#### Stabilization Method
* PER: Periodic
* OND: On Demand

#### Assignment of credit on paths
* MUL : Multi party computation
* RAND: Random computation

#### Landmark Selection
* HD: Highest Degree
* RL: Random Landmark


## Data Set

### Transaction Data: (ripple-transactions-jan-2013-aug-2016.txt)
- Format: `tx_hash, sdr, rev, currency, amount1, amount2, ledger, tag1, tag2, crawl_id, unix_timestamp`
- Above format is then converted to tx_hash, sdr, rcv, USD_amount, unix_timestamp in (transactions-in-USD-jan-2013-aug-2016.txt) using known currencies and converting them to USD (based on rate on Nov 9th).
Moreover, `total amount = amount1 * amount2/10^8`

### Trust Lines: (raw-trust-lines-2016-nov-7.txt):
- Ripple network allows IOU's. Alice can pay Bob 10$ as an IOU. Bob can then pay Claire 5$ using Alice's IOU. Alice now has to pay 5$ to Bob and 5$ to Claire when they redeem their IOU's. This feature requires both Claire and Bob to trust Alice, which is tougher in the real world. "Trust Lines" are used to create a chain of such trusted intermediatories.
- Explanation links: https://medium.com/@AlexCarrithers/xrp-vs-ious-on-ripple-what-are-they-and-which-are-banks-using-257023fc578e, https://developers.ripple.com/issued-currencies-overview.html
- Format: `account(address), lines: [], status, type`
- Lines Format: `account(address), balance, currency, limit(upper_bound), limit_peer(lower_bound), no_ripple, quality_in, quality_out`

- These are now converted as: `src, dest, lower_bound, balance, currency, upper_bound`
in (complete-parsed-trust-lines-2016-nov-7.txt).

- Finally, converting each currency to its equivalent USD amount (based on rate on Nov 9th), `format: src, dest, lower_bound, balance, upper_bound` in (all-in-USD-trust-lines-2016-nov-7.txt).

### Scripts Used
- `parse_trust_lines.py`: parses the crawled ripple graph in file (ripple-graph-jan-2013.txt), parsed content in (all-in-USD-trust-lines-2013-jan.txt).
- `parse_trust_set_transactions.py`: parses the create link transactions in file (trust-set-transactions.txt), parsed content in (links-created-in-USD-jan-2013-dec-2016.txt).

### Data Set Construction:
- Step 1: Sort `transactions-in-USD-jan-2013-aug-2016` according to timestamp. Bash script: `sort -k5 -n <filename> > <output-filename>`
- Step 2: Run `ParseFilesToGTNA.java` with params editted in code.*commited to git*
- Output will be in `data/gtna/`
#### Files created by running the above:
- ripple-degBi.txt: degOrder-bi.txt in final data set.
- ripple-degUni.txt: degOrder-uni.txt in final data set.
- ripple-lcc.graph: same as final data set.
- ripple-lcc.graph_CREDIT_LINKS: same as final data set.
- ripple-newlinks-lcc-sorted.txt: used by intermediate step.
- ripple-newlinks-lcc.txt: used by intermediate step.
- ripple-newlinks.txt: used by intermediate step.
- ripple-trans-lcc-noself.txt: Final list of transactions used in fianal data step. This file is split into 20 separete files for static simulation, named "sampleTr-x.txt".
- ripple-trans-lcc.txt: used by intermediate step.
- ripple-trans.txt: used by intermediate step.
- ripple.graph: used by intermediate step.
- ripple.graph_CREDIT_LINKS: used by intermediate step.
- setLinkEpoch.txt: *figure out what this is*

#### Final list of files used in simulation:
- degOrder-bi.txt
- degOrder-uni.txt
- ripple-lcc.graph
- ripple-lcc.graph_CREDIT_LINKS
- sampleTr-x.txt

### Dynamic Simulation Files
- `jan2013-trans-lcc-noself-uniq-*.txt`: time, value, source, destination
- `jan2013-newlinks-lcc-sorted-uniq-t*.txt`: time, source, destination, weight
- `jan2013-lcc-t0.graph`: graph of the network in GTNA understandable format
- `jan2013-lcc-t0.graph_CREDIT_LINKS`: links between nodes and their respective weights. sourse, destination, value_low, value, value_high
   - stored as source<destination
   - for src -> dest => weight = value_high - value
   - for dest -> src => weight = value - value_low

# How are results evaluated and plotted

## Static Simulation

### Program args

1. Run for trees 1 - 7
2. For all transactions (0 - 19)
3. Attempts: 1 - 10
4. Algo: Speedy Murmursi(7), Silent Whispers(0)

program args: [transaction] [algo] [attempts] [trees]

### Output folder/file structure:
- Inside dir: data/static
- Folder names: 
    - Silent Whispers:  READABLE_FILE_SW-PER-MUL-67149
    - Speedy Murmurs :  READABLE_FILE_V-DYN-67149
- Each folder inside the above will have directories numbered from 0-19 for transaction set
- Created Folder name format: 
Eg    : CREDIT_NETWORK-STATIC-1000.0-TREE_ROUTE_SILENTW-false-true-4-2000.0-RANDOM_PARTITIONER-3
Format: CREDIT_NETWORK-STATIC-[epoch_num]-TREE_ROUTE_[algo]-[dr]-[mul]-[trees]-[ri]-RANDOM_PARTITIONER-[num_attempts]
    - epoch_num: 1000.0
    - algo:
        - Silent Whispers: SILENTW
        - Speedy Murmurs : TDRAP
    - dr:
        - Silent Whispers: false 
        - Speedy Murmurs : true 
    - mul:
        - Silent Whispers: true
        - Speedy Murmurs : false 
    - ri: 2000.0
- Finally, find file named: _singles.txt 

So, for SpeedyMurmurs sim run for transaction set 3, attempts: 2 and trees: 4 the data will be at:
`data/static/READABLE_FILE_V-DYN-67149/3/CREDIT_NETWORK-STATIC-1000.0-TREE_ROUTE_TDRAP-true-false-4-2000.0-RANDOM_PARTITIONER-3/_singles.txt`

## Dynamic Simulation

### Program args:
1. Run number: 0 - 19
2. Algorithm: (0 - Silent Whispers, 7 - Speedy Murmurs, 10 - Max Flow)
3. Steps: 0 - 8 (Since simulation takes a long time, each sim is split into 9 parts)

### Output details:
- root `data/READABLE_FILE_[algo]-[nodes]`
    - algo: SM-P[step] for Speedy Murmurs, SW-P[step] - Silent Whispers, M-P[step] - Max Flow
    - nodes: for their data set 93502
- All runs are with `1 retry attempt` and `3 trees/landmarks`
- For each transaction num, the output will be inside a directory with the transaction num. For eg: `0/CREDIT_NETWORK-SM-P1-165552.45497208898-TREE_ROUTE_TDRAP-true-false-3-331.10490994417796-RANDOM_PARTITIONER-1`   
    - for transaction 0
    - algo: Speedy Murmurs
    - epoch: 165552.45497208898
    - trees: 3
    - attempts: 1
    - **TODO** what is 331.10490994417796?

## Simuation Output
- A set of files are created by running the simulation. These contain various types of data like messages sent, messages failed, total messages sent etc. These files are used by the underlying GTNA framework to plot various graphs. 
- The filesnames are self-explanatory on what values they hold.

## Graphs Plotted
- The set of files created by the simulation are plotted using GTNA.
- Wrote script to recreate the plotted figures.

## Metrics used
*This is what the paper mentions is being used while evaluating*
- Success Ratio: fraction of successful transations (higher better).
- Delay        : longest chain of messages (lower better).
- Path Length  : length of discovered path between sender and receiver (lower better).
- Stabilization: number of messages sent per epoch to stabilize trees (lower better).
