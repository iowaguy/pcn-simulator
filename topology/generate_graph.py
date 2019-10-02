#!/usr/bin/env python3

import networkx as nx
import matplotlib.pyplot as plt

def get_small_worldness(graph):
    rando_graph = nx.erdos_renyi_graph(100, 0.2)
    delta_g = nx.average_clustering(graph) / nx.average_clustering(rando_graph)
    lamda_g = nx.average_shortest_path_length(graph) / nx.average_shortest_path_length(rando_graph)
    return delta_g/lamda_g

def show(graph):
    nx.draw(graph, pos=nx.spring_layout(graph))
    plt.show()

if __name__ == '__main__':
    G = nx.powerlaw_cluster_graph(100, 5, 1.0)
    print("small worldness=" + str(get_small_worldness(G)))

