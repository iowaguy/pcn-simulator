#!/usr/bin/env python3

import collections
import json
import logging
import math
import matplotlib.pyplot as plt
import networkx as nx
import numpy as np
import pcn.plot_data_sets as dsplot
import random
import statistics as stat

class Topology:
    def __init__(self, base_topology=None, nodes=None, connection_parameter=None, graph=None, balance_multiplier=1):
        # number of edges to connect to existing nodes
        self.__connection_parameter = connection_parameter
        self.__base_topolgies = {'hybrid':self.__gen_hybrid_topology, 'smallworld':self.__gen_smallworld_topology, 'random':self.__gen_random_topology, 'scalefree':self.__gen_scalefree_topology, 'scalefree_max':self.__gen_scalefree_topology_with_max_degree}

        if graph:
            print(f"Is connected? {nx.is_connected(graph)}")
            largest_cc = max(nx.connected_components(graph), key=len)
            graph = graph.subgraph(largest_cc).copy()
            self.__graph = nx.relabel.convert_node_labels_to_integers(graph)

            self.__link_weights = {}
            for src, dest in self.__graph.edges():
                self.__link_weights[(src, dest)] = self.__graph.get_edge_data(src, dest)['capacity']*balance_multiplier
                if (dest, src) not in self.__link_weights:
                    self.__link_weights[(dest, src)] = 0
        else:
            self.__graph = self.__base_topolgies[base_topology](nodes)
            self.__link_weights = None


    def in_largest_connected_component(self, n):
        return n in self.largest_cc


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
