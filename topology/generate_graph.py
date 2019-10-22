#!/usr/bin/env python3

import sys
import yaml
import networkx as nx
import matplotlib.pyplot as plt
import logging
import random

node_count = 'node_count'
tx_count = 'tx_count'
tx_distro = 'tx_distribution'
base_topology = 'base_topology'

class Topology:
    def __init__(self, base_topology, nodes):
        self.base_topolgies = {'hybrid':self.gen_hybrid_topology, 'smallworld':self.gen_smallworld_topology, 'random':self.gen_random_topology, 'scalefree':self.gen_scalefree_topology}
        self.graph = self.base_topolgies[base_topology](nodes)

    def get_small_worldness(self):
        rando_graph = nx.erdos_renyi_graph(len(self.graph.nodes), 0.2)
        delta_g = nx.average_clustering(self.graph) / nx.average_clustering(rando_graph)
        lamda_g = nx.average_shortest_path_length(self.graph) / nx.average_shortest_path_length(rando_graph)
        return delta_g/lamda_g

    def show(self):
        nx.draw(self.graph, pos=nx.spring_layout(self.graph))
        plt.show()

    def gen_smallworld_topology(self, nodes):
        # Each node is joined with its k nearest neighbors in a ring topology
        k = 5

        # The probability of rewiring each edge
        p = 0.5
        return nx.watts_strogatz_graph(nodes, k, p)

    def gen_random_topology(self, nodes):
        # probability for edge creation
        p = 0.5
        return nx.erdos_renyi_graph(nodes, p)

    def gen_scalefree_topology(self, nodes):
        # number of edges to connect to existing nodes
        num_new_edges=5
        return nx.barabasi_albert_graph(nodes, num_new_edges)

    def gen_hybrid_topology(self, nodes):
        probability_of_triangle=1.0
        random_edges_per_node=5
        return nx.powerlaw_cluster_graph(nodes, random_edges_per_node, probability_of_triangle)

class TxDistro:
    def __init__(self, distribution):
        self.tx_distributions = {'powerlaw':self.sample_tx_pareto}
        self.distribution = distribution

    def sample_tx_pareto(self, n):
        alpha=1.16 # from the pareto principle, i.e. 80/20 rule
        return [random.paretovariate(alpha) for i in range(0, n)]

    def sample(self, n=1):
        return self.tx_distributions[self.distribution](n)




if __name__ == '__main__':
    with open(sys.argv[1], 'r') as stream:
        try:
            configs = yaml.safe_load(stream)
        except yaml.YAMLError as e:
            print(exc)
            exit()

    topo = Topology(configs[base_topology], configs[node_count])
    txdist = TxDistro(configs[tx_distro])


    # topo.show()
    # print("small worldness=" + str(topo.get_small_worldness()))

    # print(tx_dist.sample(5))
