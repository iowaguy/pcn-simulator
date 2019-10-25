#!/usr/bin/env python3

import sys
import yaml
import networkx as nx
import matplotlib.pyplot as plt
import logging
import random
import math

node_count = 'node_count'
tx_count = 'tx_count'
tx_value_distro = 'tx_value_distribution'
tx_participant_distro = 'tx_participant_distribution'
base_topology = 'base_topology'
log_level = 'log_level'

class Topology:
    def __init__(self, base_topology, nodes):
        self.__base_topolgies = {'hybrid':self.__gen_hybrid_topology, 'smallworld':self.__gen_smallworld_topology, 'random':self.__gen_random_topology, 'scalefree':self.__gen_scalefree_topology}
        self.__graph = self.__base_topolgies[base_topology](nodes)

    def get_small_worldness(self):
        rando_graph = nx.erdos_renyi_graph(len(self.__graph.nodes), 0.2)
        delta_g = nx.average_clustering(self.__graph) / nx.average_clustering(rando_graph)
        lamda_g = nx.average_shortest_path_length(self.__graph) / nx.average_shortest_path_length(rando_graph)
        return delta_g/lamda_g

    def show(self):
        nx.draw(self.__graph, pos=nx.spring_layout(self.__graph))
        plt.show()

    def __gen_smallworld_topology(self, nodes):
        # Each node is joined with its k nearest neighbors in a ring topology
        k = 5

        # The probability of rewiring each edge
        p = 0.5
        return nx.watts_strogatz_graph(nodes, k, p)

    def __gen_random_topology(self, nodes):
        # probability for edge creation
        p = 0.5
        return nx.erdos_renyi_graph(nodes, p)

    def __gen_scalefree_topology(self, nodes):
        # number of edges to connect to existing nodes
        num_new_edges=5
        return nx.barabasi_albert_graph(nodes, num_new_edges)

    def __gen_hybrid_topology(self, nodes):
        probability_of_triangle=1.0
        random_edges_per_node=5
        return nx.powerlaw_cluster_graph(nodes, random_edges_per_node, probability_of_triangle)

    def full_knowledge_edge_weight_gen(self, tx_list, routingalgo=nx.shortest_path):
        max_weight = {}
        cur_weight = {}

        # set all edge weights to zero
        logging.info("Setting edge weights to zero")
        for source, dest in self.edges:
            max_weight[(source, dest)] = 0
            cur_weight[(source, dest)] = 0

        for source, dest, val in tx_list:
            logging.debug("Searching for path...")
            r_i = routingalgo(self.__graph, source=source, target=dest)

            for i in range(0, len(r_i)-1):
                node1 = r_i[i]
                node2 = r_i[i+1]

                logging.info("Applying transaction...")
                cur_weight[(node1, node2)] += val

                if cur_weight[(node2, node1)] > 0:
                    logging.debug(f"{node1} -> {node2}: {cur_weight[(node1, node2)]}; {node2} -> {node1}: {cur_weight[(node2, node1)]}")
                    min_weight = min(cur_weight[(node1, node2)], cur_weight[(node2, node1)])
                    cur_weight[(node1, node2)] -= min_weight
                    cur_weight[(node2, node1)] -= min_weight
                    logging.debug(f"Updated weights: {node1} -> {node2}: {cur_weight[(node1, node2)]}; {node2} -> {node1}: {cur_weight[(node2, node1)]}")
                max_weight[(node1, node2)] = max(max_weight[(node1, node2)], cur_weight[(node1, node2)])

        return max_weight

    @property
    def graph(self):
        return self.__graph

    @graph.setter
    def graph(self, graph):
        self.__graph = graph

    @property
    def edges(self):
        out = []
        for s, d in nx.edges(self.__graph):
            out.append((s,d))
            out.append((d,s))
        return out


class TxDistro:
    # future considerations for sampling
    # when sampling sources, most likely are going to be nodes with low connectivity
    # when sampling dests, most likely are going to be nodes with high connectivity
    # short transactions are more likely than long ones
    # some pairs are more likely to transact that other pairs

    def __init__(self, value_distro, participant_distro, topology):
        self.__tx_value_distribution_types = {'powerlaw':self.__sample_tx_pareto}
        self.__tx_participant_distribution_types = {'random':self.__sample_random_nodes, 'powerlaw':self.__sample_pairs_pareto_dist}
        self.__value_distribution = value_distro
        self.__participant_distribution = participant_distro
        self.__topology = topology
        self.__source_nodes_distro = list(self.__topology.graph.nodes)
        random.shuffle(self.__source_nodes_distro)
        self.__dest_nodes_distro = list(self.__topology.graph.nodes)
        random.shuffle(self.__dest_nodes_distro)

    def sample(self, n=1):
        values = self.__tx_value_distribution_types[self.__value_distribution](n)

        out = []
        for i in range(0, n):
            source, dest = self.__tx_participant_distribution_types[self.__participant_distribution]()
            out.append((source, dest, values[i]))

        return out

    def __sample_tx_pareto(self, n):
        alpha=1.16 # from the pareto principle, i.e. 80/20 rule
        return [random.paretovariate(alpha) for i in range(0, n)]

    def __sample_random_nodes(self):
        return random.sample(self.__topology.graph.nodes, 2)

    def __sample_pairs_pareto_dist(self):
        source = self.__sample_pareto_dist(self.__source_nodes_distro, max=5)
        dest = self.__sample_pareto_dist(self.__dest_nodes_distro, max=5)

        logging.debug(f"dest={dest}; source={source}")
        while dest == source:
            dest = self.__sample_pareto_dist(self.__topology.graph.nodes, max=5)
            logging.debug(f"dest={dest}; source={source}")

        return (source, dest)

    def __sample_pareto_dist(self, l, max=None):
        l = list(l)
        alpha=1.16 # from the pareto principle, i.e. 80/20 rule
        bucket_width = 5.0/len(l)

        # sample number, need to subtract 1 so that distro starts at zero.
        # pareto normally starts at one.
        num = random.paretovariate(alpha) - 1

        while max and max < num:
            num = random.paretovariate(alpha) - 1

        # find corresponding bucket
        bucket = math.floor(num/bucket_width)
        logging.debug(f"num={num}; bucket-width={bucket_width}; bucket={bucket}; node={l[bucket]}")
        return l[bucket]


def convert_topo_to_gtna(topo):

    edge_map = {}
    for s, d in topo.edges:
        if str(s) in edge_map:
            edge_map[str(s)].append(str(d))
        else:
            edge_map[str(s)] = [str(d)]

    filename = "topology.graph"
    with open(filename, 'w') as f:
        f.write("# Name of the Graph:\n")
        f.write(f"G (Nodes = {len(topo.graph)})\n")
        f.write("# Number of Nodes:\n")
        f.write(f"{len(topo.graph)}\n")
        f.write("# Number of Edges:\n")
        f.write(f"{len(topo.edges)}\n\n")

        for source, dest_list in edge_map.items():
            dest_str = ";".join(dest_list)
            f.write(f"{source}:{dest_str}\n")


def convert_txs_to_gtna():
    print("not supported")

if __name__ == '__main__':
    with open(sys.argv[1], 'r') as stream:
        try:
            configs = yaml.safe_load(stream)
        except yaml.YAMLError as e:
            logging.error(e)
            exit(1)
    if log_level in configs:
        logging.basicConfig(level=configs[log_level].upper())
    else:
        logging.basicConfig(level=logging.ERROR)

    topo = Topology(configs[base_topology], configs[node_count])
    txdist = TxDistro(configs[tx_value_distro], configs[tx_participant_distro], topo)



    # print("small worldness=" + str(topo.get_small_worldness()))

    # print(txdist.sample(5))

    # topo.full_knowledge_edge_weight_gen(txdist.sample(configs[tx_count]))
    # print(topo.edges)
    convert_topo_to_gtna(topo)

    # print(topo.full_knowledge_edge_weight_gen(txdist.sample(configs[tx_count])))
    # topo.show()
