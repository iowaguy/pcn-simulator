#!/usr/bin/env python3

import collections
import json
import logging
import math
import matplotlib.pyplot as plt
import networkx as nx
import numpy as np
import plot_data_sets as dsplot
import random
import statistics as stat

class TxDistro:
    # future considerations for sampling
    # when sampling sources, most likely are going to be nodes with low connectivity
    # when sampling dests, most likely are going to be nodes with high connectivity
    # short transactions are more likely than long ones
    # some pairs are more likely to transact that other pairs

    def __init__(self, value_distro, participant_distro, topology):
        self.__tx_value_distribution_types = {'pareto':self.__sample_tx_pareto, 'constant':self.__sample_tx_constant, 'exponential':self.__sample_tx_exponential, 'poisson':self.__sample_tx_poisson, 'normal':self.__sample_tx_normal}
        self.__tx_participant_distribution_types = {'random':self.__sample_random_nodes, 'pareto':self.__sample_pairs_pareto_dist, 'poisson':self.__sample_pairs_poisson_dist, 'normal':self.__sample_pairs_normal_dist}
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
