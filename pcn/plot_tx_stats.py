#!/usr/bin/env python3

import pandas as pd
import json
import networkx as nx
import matplotlib.pyplot as plt
import numpy as np
import pcn.exp_analysis


def transactions_vs_betweenness_centrality(dataset_path, exp_path=None, tx_counts=[]):
    tx_counts = sanity_check_tx_counts(exp_path, tx_counts)
    __transactions_vs_x(dataset_path + "/betweenness_centrality_raw.txt", tx_counts, \
                                 'Betweenness centrality (normalized)')


def transactions_vs_tree_depth(exp_path, tx_counts=[]):
    tx_counts = sanity_check_tx_counts(exp_path, tx_counts)
    __transactions_vs_x(exp_path + "/cnet-nodeDepths.txt", tx_counts, 'Distance from root')


def transactions_vs_subtree_size(exp_path, tx_counts=[]):
    tx_counts = sanity_check_tx_counts(exp_path, tx_counts)
    __transactions_vs_x(exp_path + "/cnet-subtreeSize.txt", tx_counts, 'Subtree size')


def __transactions_vs_x(path, tx_counts, xlabel, roots=[64, 36, 43], nodes=10000):
    df = pd.read_csv(path, header=None, delim_whitespace=True)

    # if there are two columns, the first one is an index and should be deleted
    if len(df.columns) > 1:
        # delete index column
        del df[0]

        # rename the remaining column
        df.columns = [0]

    if len(df) == nodes:
        df = pd.concat([df, df, df], ignore_index=True)
    
    df['tx_counts'] = tx_counts

    plt.xlabel(xlabel)
    plt.ylabel('# of transactions')

    lower = 0
    upper = lower + nodes

    for i, root in enumerate(roots):
        data_per_tree = df.iloc[lower:upper]

        # plt.scatter(df[0].iloc[lower + root], df['tx_counts'].iloc[lower + root], s=10, c='r')
        plt.scatter(data_per_tree[0], data_per_tree['tx_counts'], s=10)
        plt.scatter(data_per_tree[0].iloc[root], data_per_tree['tx_counts'].iloc[root], s=10, c='r')
        
        r = np.corrcoef(data_per_tree[0], data_per_tree['tx_counts'])
        print(f'{xlabel}, tree {i+1}, r={r[0][1]}')        
        lower += nodes
        upper = lower + nodes
        plt.show()        
