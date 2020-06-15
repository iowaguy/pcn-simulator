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
import graph_analysis as ga
import lightning_utils as ln

node_count = 'node_count'
tx_count = 'tx_count'
tx_value_distro = 'tx_value_distribution'
tx_participant_distro = 'tx_participant_distribution'
base_topology = 'base_topology'
log_level = 'log_level'
dataset_base = '../datasets'

class Topology:
    def __init__(self, base_topology=None, nodes=None, connection_parameter=None, graph=None):
        # number of edges to connect to existing nodes
        self.__connection_parameter = connection_parameter
        self.__base_topolgies = {'hybrid':self.__gen_hybrid_topology, 'smallworld':self.__gen_smallworld_topology, 'random':self.__gen_random_topology, 'scalefree':self.__gen_scalefree_topology, 'scalefree_max':self.__gen_scalefree_topology_with_max_degree}

        if graph:
            self.__graph = nx.relabel.convert_node_labels_to_integers(graph)
            self.__link_weights = {}
            for src, dest in self.__graph.edges():
                self.__link_weights[(src, dest)] = self.__graph.get_edge_data(src, dest)['capacity']
                if (dest, src) not in self.__link_weights:
                    self.__link_weights[(dest, src)] = 0
        else:
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
        k = self.__connection_parameter['k']

        # The probability of rewiring each edge
        p = self.__connection_parameter['p']
#        return nx.connected_watts_strogatz_graph(nodes, k, p)
        return nx.newman_watts_strogatz_graph(nodes, k, p)
    
    def __gen_random_topology(self, nodes):
        # probability for edge creation
        p = self.__connection_parameter
        return nx.erdos_renyi_graph(nodes, p)

    def __gen_scalefree_topology(self, nodes):
        return nx.barabasi_albert_graph(nodes, self.__connection_parameter)

    def __gen_scalefree_topology_with_max_degree(self, nodes):
        return nx.barabasi_albert_graph(nodes, self.__connection_parameter)

    def __my_ba_with_max_degree(self, n, m, max_degree):
        if m < 1 or m >= n:
            raise nx.NetworkXError("Barabási–Albert network must have m >= 1"
                                   " and m < n, m = %d, n = %d" % (m, n))

        # Add m initial nodes (m0 in barabasi-speak)
        G = nx.empty_graph(m)
        # Target nodes for new edges
        targets = list(range(m))
        # List of existing nodes, with nodes repeated once for each adjacent edge
        repeated_nodes = []
        # Start adding the other n-m nodes. The first node is m.
        source = m
        while source < n:
            # Add edges to m nodes from the source.
            G.add_edges_from(zip([source] * m, targets))
            # Add one node to the list for each new edge just created.
            repeated_nodes.extend(targets)
            # And the new node "source" has m edges to add to the list.
            repeated_nodes.extend([source] * m)
            # Now choose m unique nodes from the existing nodes
            # Pick uniformly from repeated_nodes (preferential attachment)
            targets = _random_subset(repeated_nodes, m, seed)
            source += 1
        return G


    def __gen_hybrid_topology(self, nodes):
        probability_of_triangle=1.0
        random_edges_per_node=self.__connection_parameter
        return nx.powerlaw_cluster_graph(nodes, random_edges_per_node, probability_of_triangle)

    def full_knowledge_edge_weight_gen(self, tx_list, routingalgo=nx.shortest_path, value_multiplier=1.0, mult_probability=1.0, tx_inclusion_probability=1.0, min_channel_balance=0):
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
            try:
                r_i = routingalgo(self.__graph, source=source, target=dest)
            except nx.exception.NetworkXNoPath as e:
                continue

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

            if min_channel_balance:
                if max_weight[k] < min_channel_balance:
                    max_weight[k] = min_channel_balance

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
        return np.random.normal(30, 10, n)

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
        bp=2
        loc=len(l)/bp

        num = np.random.normal(loc, 2000)
        while num < 0 or num > len(l):
            num = np.random.normal(loc, 2000)
        # find corresponding bucket
        node = math.floor(num)
        logging.debug(f"num={num};  node={node}")
        return node


    def __sample_poisson_dist(self, l, max=None):
        l = list(l)
        bp=2
        lam=len(l)/bp

        num = np.random.poisson(lam)
        return l[num]

    def __sample_pareto_dist(self, l, max=None):
        l = list(l)
        alpha=1.16 # from the pareto principle, i.e. 80/20 rule
        bp=5.0
        bucket_width = bp/len(l)

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
        s_str = str(s)
        d_str = str(d)
        if s_str in edge_map:
            edge_map[s_str].append(d_str)
        else:
            edge_map[s_str] = [d_str]

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


    if 'load_topo' in configs:
        load_topo_type = configs['load_topo'].get('type', 'gtna')
        if load_topo_type == 'gtna':
            # TODO read GTNA files
            raise NotImplementedError()
        elif load_topo_type == 'lightning_snapshot':
            G = ln.load_lightning_topo(configs['load_topo']['ln_snapshot_file'])
            topo = Topology(graph=G)
        else:
            raise NotImplementedError("{load_topo_type} not yet supported")

    else:
        topo = Topology(configs.get(base_topology, 'scalefree'),
                        configs.get(node_count, 10000),
                        configs.get('connection_parameter', 2))

    txdist = TxDistro(configs[tx_value_distro], configs[tx_participant_distro], topo)



    # print("small worldness=" + str(topo.get_small_worldness()))

    # print(txdist.sample(5))

    # topo.full_knowledge_edge_weight_gen(txdist.sample(configs[tx_count]))
    # print(topo.edges)

    txs = txdist.sample(configs[tx_count])

    if 'load_topo' in configs:
        load_channels_type = configs['load_topo'].get('type', 'gtna')
        if load_channels_type == 'gtna':
            # TODO read GTNA files
            raise NotImplementedError()
        elif load_channels_type == 'lightning_snapshot':
            # already done
            pass

    else:
        if 'value_multiplier' in configs:
            topo.full_knowledge_edge_weight_gen(txs, value_multiplier=configs.get('value_multiplier', 1),
                                                mult_probability=configs.get('multiplier_probability', 1),
                                                tx_inclusion_probability=configs.get('tx_inclusion_probability', 1),
                                                min_channel_balance=configs.get('min_channel_balance', 0))
        else:
            topo.full_knowledge_edge_weight_gen(txs, min_channel_balance=configs.get('min_channel_balance', 0))


    convert_topo_to_gtna(topo)
    convert_credit_links_to_gtna(topo)
    convert_txs_to_gtna(txs)
    # # topo.show()


    new_dataset_path = dataset_base + '/' + configs.get('name')
    Path(new_dataset_path).mkdir(parents=True, exist_ok=True)

    for i in range(1, 11):
        new_file = f"{new_dataset_path}/newlinks-{i}.txt"
        open(new_file, 'a').close()
        # shutil.move(new_file, f"{new_dataset_path}/{fname}")

    # create empty file
    open(f"{new_dataset_path}/newlinks.txt", 'a').close()

    nodes = ga.select_n_by_method(".", method=ga.betweenness_centrality, top_n=len(topo.graph))
    with open("betweenness_centrality.txt", "w") as f:
        f.write(str(nodes))

    files_to_move = ["topology.graph", "topology.graph_CREDIT_LINKS", "transactions.txt", "betweenness_centrality.txt"]
    for fname in files_to_move:
        shutil.move(fname, f"{new_dataset_path}/{fname}")

    shutil.copy(spec_file, f"{new_dataset_path}/{spec_file}")

    dsplot.plot_graph(new_dataset_path)
    dsplot.plot_txs(new_dataset_path)


