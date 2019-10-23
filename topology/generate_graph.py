#!/usr/bin/env python3

import sys
import yaml
import networkx as nx
import matplotlib.pyplot as plt
import logging
import random

node_count = 'node_count'
tx_count = 'tx_count'
tx_value_distro = 'tx_value_distribution'
tx_participant_distro = 'tx_participant_distribution'
base_topology = 'base_topology'

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
        for source, dest in nx.edges(self.__graph):
            max_weight[(source, dest)] = 0
            max_weight[(dest, source)] = 0
            cur_weight[(source, dest)] = 0
            cur_weight[(dest, source)] = 0

        for source, dest, val in tx_list:
            r_i = routingalgo(self.__graph, source=source, target=dest)
            for i in range(0, len(r_i)-1):
                node1 = r_i[i]
                node2 = r_i[i+1]

                # apply tx
                cur_weight[(node1, node2)] += val

                if cur_weight[(node2, node1)] > 0:
                    min_weight = min(cur_weight[(node1, node2)], cur_weight[(node2, node1)])
                    cur_weight[(node1, node2)] -= min_weight
                    cur_weight[(node2, node1)] -= min_weight

                max_weight[(node1, node2)] = max(max_weight[(node1, node2)], cur_weight[(node1, node2)])

        return max_weight

    @property
    def graph(self):
        return self.__graph

    @graph.setter
    def graph(self, graph):
        self.__graph = graph


class TxDistro:
    # future considerations for sampling
    # when sampling sources, most likely are going to be nodes with low connectivity
    # when sampling dests, most likely are going to be nodes with high connectivity
    # short transactions are more likely than long ones
    # some pairs are more likely to transact that other pairs

    def __init__(self, value_distro, participant_distro, topology):
        self.__tx_value_distribution_types = {'powerlaw':self.__sample_tx_pareto}
        self.__tx_participant_distribution_types = {'random':self.__sample_random_nodes}
        self.__value_distribution = value_distro
        self.__participant_distribution = participant_distro
        self.__topology = topology

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




if __name__ == '__main__':
    logging.basicConfig(level=logging.ERROR)
    with open(sys.argv[1], 'r') as stream:
        try:
            configs = yaml.safe_load(stream)
        except yaml.YAMLError as e:
            logging.error(e)
            exit(1)

    topo = Topology(configs[base_topology], configs[node_count])
    txdist = TxDistro(configs[tx_value_distro], configs[tx_participant_distro], topo)



    # print("small worldness=" + str(topo.get_small_worldness()))

    # print(txdist.sample(5))

    # topo.full_knowledge_edge_weight_gen(txdist.sample(configs[tx_count]))
    print(topo.full_knowledge_edge_weight_gen(txdist.sample(configs[tx_count])))
    # topo.show()
