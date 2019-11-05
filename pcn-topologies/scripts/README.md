# Script Usage
./generate_graph.py [CONFIG_FILE_PATH]

## Example
./generate_graph.py test.yml

Also see `gen.sh` for how to run in the background.

# Parameters
```
node_count: 100000   <--- any integer
tx_count: 1000000    <--- any integer
tx_value_distribution: powerlaw <--- [powerlaw, random]
tx_participant_distribution: powerlaw <--- [powerlaw, random]
base_topology: scalefree <--- [smallworld, scalefree, hybrid, random]
log_level: error <--- [debug, info, warn, error]
```

# Topology Types
Any of the types listed above will be accepted. The smallworld topology is a Watts-Strogratz; the scalefree topology is a Barabasi-Albert; the hybrid topology is a Barabasi-Albert with a high chance of triangles forming, which means that it will have some smallworld properties; the random topology is an Erdos-Renyi.

# Transaction Distributions
In either distribution, transaction values, sources, and sinks are sampled independently. This is an area for improvement.

# Timing
For reference, a scalefree topology with 100k nodes and 1M transactions will take 2-3 hours to generate.