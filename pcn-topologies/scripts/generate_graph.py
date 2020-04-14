#!/usr/bin/env python3

import sys
import yaml
import networkx as nx
import matplotlib.pyplot as plt
import logging
import random
import math
import numpy as np
import plot_data_sets as dsplot
from pathlib import Path
import shutil

node_count = 'node_count'
tx_count = 'tx_count'
tx_value_distro = 'tx_value_distribution'
tx_participant_distro = 'tx_participant_distribution'
base_topology = 'base_topology'
log_level = 'log_level'
dataset_base = '../datasets'

class Topology:
    def __init__(self, base_topology, nodes):
        self.__base_topolgies = {'hybrid':self.__gen_hybrid_topology, 'smallworld':self.__gen_smallworld_topology, 'random':self.__gen_random_topology, 'scalefree':self.__gen_scalefree_topology}
        self.__graph = self.__base_topolgies[base_topology](nodes)
        self.__link_weights = None

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
        num_new_edges=2
        return nx.barabasi_albert_graph(nodes, num_new_edges)

    def __gen_hybrid_topology(self, nodes):
        probability_of_triangle=1.0
        random_edges_per_node=2
        return nx.powerlaw_cluster_graph(nodes, random_edges_per_node, probability_of_triangle)

    def full_knowledge_edge_weight_gen(self, tx_list, routingalgo=nx.shortest_path, value_multiplier=1.0, mult_probability=1.0, tx_inclusion_probability=1.0):
        max_weight = {}
        cur_weight = {}

        # set all edge weights to zero
        logging.info("Setting edge weights to zero")
        for source, dest in self.edges:
            max_weight[(source, dest)] = 0
            cur_weight[(source, dest)] = 0

        for source, dest, val in tx_list:
            # do a biased coin flip, if result is 1, include this transaction in the weight assignment
            if tx_inclusion_probability != 1.0 and np.random.binomial(1, tx_inclusion_probability) == 0:
                continue

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

        for k in max_weight:
            # do a biased coin flip, if result is 1 and multiplier is not 1.0, use multiplier
            if np.random.binomial(1, mult_probability) == 1 and value_multiplier != 1.0:
                max_weight[k] = max_weight[k]*value_multiplier

        self.__link_weights = max_weight
        return max_weight

    # def uniform_edge_weight_gen(self, tx_list, value_multiplier=1.0):
    #     sums = 0
    #     for src, dest, val in tx_list:
    #         sums += val

    #     link_avg = sums/nx.edges(self.__graph)

    #     weights = {}
    #     for src, dest, val in tx_list:
    #         weights[(src, dest)] = link_avg

    #     self.__link_weights = weights

    #     return weights
    
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

    @property
    def link_credit(self):
        return self.__link_weights

class TxDistro:
    # future considerations for sampling
    # when sampling sources, most likely are going to be nodes with low connectivity
    # when sampling dests, most likely are going to be nodes with high connectivity
    # short transactions are more likely than long ones
    # some pairs are more likely to transact that other pairs

    def __init__(self, value_distro, participant_distro, topology):
        self.__tx_value_distribution_types = {'powerlaw':self.__sample_tx_pareto, 'constant':self.__sample_tx_constant, 'exponential':self.__sample_tx_exponential, 'poisson':self.__sample_tx_poisson, 'normal':self.__sample_tx_normal}
        self.__tx_participant_distribution_types = {'random':self.__sample_random_nodes, 'powerlaw':self.__sample_pairs_pareto_dist, 'poisson':self.__sample_pairs_poisson_dist, 'normal':self.__sample_pairs_normal_dist}
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

    def __sample_tx_poisson(self, n):
        return np.random.poisson(10, n)

    def __sample_tx_normal(self, n):
        return np.random.normal(10, 10, n)

    def __sample_tx_exponential(self, n):
        return np.random.exponential(10, n)
        
    def __sample_tx_constant(self, n):
        return [1 for i in range(0, n)]
    
    def __sample_tx_pareto(self, n):
        alpha=1.16 # from the pareto principle, i.e. 80/20 rule
        return [random.paretovariate(alpha) for i in range(0, n)]

    def __sample_random_nodes(self):
        return random.sample(self.__topology.graph.nodes, 2)

    def __sample_pairs(self, func):
        source = func(self.__source_nodes_distro, max=5)
        dest = func(self.__dest_nodes_distro, max=5)

        logging.debug(f"dest={dest}; source={source}")
        while dest == source:
            dest = func(self.__dest_nodes_distro, max=5)
            logging.debug(f"dest={dest}; source={source}")

        return (source, dest)
    
    def __sample_pairs_pareto_dist(self):
        return self.__sample_pairs(self.__sample_pareto_dist)

    def __sample_pairs_poisson_dist(self):
        return self.__sample_pairs(self.__sample_poisson_dist)

    def __sample_pairs_normal_dist(self):
        return self.__sample_pairs(self.__sample_normal_dist)

    def __sample_normal_dist(self, l, max=None):
        l = list(l)
        loc=len(l)/2

        num = np.random.normal(loc, 2000)
        while num < 0 or num > len(l):
            num = np.random.normal(loc, 2000)
        # print(len(l))
        # print(bucket_width)
        # print(num)
        # # find corresponding bucket
        node = math.floor(num)
        # print(bucket)
        logging.debug(f"num={num};  node={node}")
        #return l[bucket]
        return node


    def __sample_poisson_dist(self, l, max=None):
        l = list(l)
        lam=len(l)/2

        num = np.random.poisson(lam)
        return l[num]

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


def convert_topo_to_gtna(topo, name="topology"):
    edge_map = {}
    for s, d in topo.edges:
        if str(s) in edge_map:
            edge_map[str(s)].append(str(d))
        else:
            edge_map[str(s)] = [str(d)]

    with open(f"{name}.graph", 'w') as f:
        f.write("# Name of the Graph:\n")
        f.write(f"G (Nodes = {len(topo.graph)})\n")
        f.write("# Number of Nodes:\n")
        f.write(f"{len(topo.graph)}\n")
        f.write("# Number of Edges:\n")
        f.write(f"{len(topo.edges)}\n\n")

        for source, dest_list in edge_map.items():
            dest_str = ";".join(dest_list)
            f.write(f"{source}:{dest_str}\n")

def convert_credit_links_to_gtna(topo, name="topology"):
    with open(f"{name}.graph_CREDIT_LINKS", 'w') as f:
        f.write("# Graph Property Class\n")
        f.write("treeembedding.credit.CreditLinks\n")
        f.write("# Key\n")
        f.write("CREDIT_LINKS\n")

        # print(topo.link_credit)
        edges = {}
        for s, d in topo.graph.edges:
            if s < d:
                cur = roundup(topo.link_credit[(s,d)])
                max = roundup(cur + topo.link_credit[(d, s)])
                line = f"{s} {d} 0.0 {cur} {max}\n"
                f.write(line)


def convert_txs_to_gtna(transactions, name="transactions"):
    with open(f"{name}.txt", 'w') as f:
        counter = 0
        for source, dest, value in transactions:
            f.write(f"{counter} {rounddown(value)} {source} {dest}\n")
            counter += 1


def roundup(x):
    return math.ceil(x * 10) / 10.0

def rounddown(x):
    return math.floor(x * 10) / 10.0

if __name__ == '__main__':
    spec_file = sys.argv[1]
    with open(spec_file, 'r') as stream:
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

    txs = txdist.sample(configs[tx_count])

    if 'value_multiplier' in configs:
        topo.full_knowledge_edge_weight_gen(txs, value_multiplier=configs.get('value_multiplier', 1),
                                            mult_probability=configs.get('multiplier_probability', 1),
                                            tx_inclusion_probability=configs.get('tx_inclusion_probability', 1))
    else:
        topo.full_knowledge_edge_weight_gen(txs)

    convert_topo_to_gtna(topo)
    convert_credit_links_to_gtna(topo)
    convert_txs_to_gtna(txs)
    # topo.show()

    new_dataset_path = dataset_base + '/' + configs.get('name')
    Path(new_dataset_path).mkdir(parents=True, exist_ok=True)

    for i in range(1, 11):
        open(f"{new_dataset_path}/newlinks-{i}.txt", 'a').close()

    open(f"{new_dataset_path}/newlinks.txt", 'a').close()

    files_to_move = ["topology.graph", "topology.graph_CREDIT_LINKS", "transactions.txt"]
    for fname in files_to_move:
        shutil.move(fname, f"{new_dataset_path}/{fname}")

    shutil.copy(spec_file, f"{new_dataset_path}/{spec_file}")

    dsplot.plot_graph(new_dataset_path)
    dsplot.plot_txs(new_dataset_path)
