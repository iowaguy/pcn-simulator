#!/usr/bin/env python3

import pandas as pd
import json
import networkx as nx
import matplotlib.pyplot as plt
import numpy as np
import pcn.exp_analysis

def transactions_vs_betweenness_centrality(dataset_path, exp_path=None,
                                           tx_counts=[], write=False,
                                           plot_name=None):
    tx_counts = pcn.exp_analysis.sanity_check_tx_counts(exp_path, tx_counts)
    __transactions_vs_x(dataset_path + "/betweenness_centrality_raw.txt",
                        tx_counts, 'Betweenness centrality (normalized)',
                        write=write, plot_name=f'{plot_name}-centrality')


def transactions_vs_tree_depth(exp_path, tx_counts=[], dataset_path=None,
                               write=False, plot_name=None):
    tx_counts = pcn.exp_analysis.sanity_check_tx_counts(exp_path, tx_counts)
    __transactions_vs_x(exp_path + "/cnet-nodeDepths.txt", tx_counts,
                        'Distance from root', dataset_path=dataset_path,
                        write=write, plot_name=f'{plot_name}-tree-depth')


def transactions_vs_subtree_size(exp_path, tx_counts=[], dataset_path=None,
                                 write=False, plot_name=None):
    tx_counts = pcn.exp_analysis.sanity_check_tx_counts(exp_path, tx_counts)
    __transactions_vs_x(exp_path + "/cnet-subtreeSize.txt", tx_counts,
                        'Subtree size', dataset_path=dataset_path,
                        write=write, plot_name=f'{plot_name}-subtree')


def __transactions_vs_x(path, tx_counts, xlabel, roots=[64, 36, 43], nodes=10000,
                        dataset_path=None, k=5, write=False, plot_name=None):
    df = pd.read_csv(path, header=None, delim_whitespace=True)
    k_most_central = []
    if dataset_path:
        k_most_central = pd.read_csv(dataset_path + '/betweenness_centrality_raw.txt',
                                    header=None, delim_whitespace=True) \
                                    .nlargest(k, 0).index.values

    # if there are two columns, the first one is an index and should be deleted
    if len(df.columns) > 1:
        # delete index column
        del df[0]

        # rename the remaining column
        df.columns = [0]

    if len(df) == nodes:
        df = pd.concat([df, df, df], ignore_index=True)
    
    df['tx_counts'] = tx_counts

    lower = 0
    upper = lower + nodes
    for i, root in enumerate(roots):
        data_per_tree = df.iloc[lower:upper]

        plt.scatter(data_per_tree[0], data_per_tree['tx_counts'], s=10,
                    label='Node')

        # plot roots in red
        plt.scatter(data_per_tree[0].iloc[root],
                    data_per_tree['tx_counts'].iloc[root], s=10, c='r',
                    label='Root')

        # plot k most central nodes
        for index, j in enumerate(k_most_central):
            if index == 0:
                plt.scatter(data_per_tree[0].iloc[j],
                            data_per_tree['tx_counts'].iloc[j], s=10, c='m',
                            label=f'Top {k} Most Central')
            else:
                # Don't repeat labels
                plt.scatter(data_per_tree[0].iloc[j],
                            data_per_tree['tx_counts'].iloc[j], s=10, c='m')

        r = np.corrcoef(data_per_tree[0], data_per_tree['tx_counts'])
        print(f'{xlabel}, tree {i+1}, r={r[0][1]}')        
        lower += nodes
        upper = lower + nodes
        plt.xlabel(xlabel)
        plt.ylabel('# of transactions')
        plt.legend()
        plt.tight_layout()
        if write:
            if not plot_name:
                raise Error("If write option is specified, then plot name must be provided.")
            plt.savefig(f'{plot_name}-tree{i+1}.png', dpi=300)

            # Clear the figure so I can plot again.
            plt.close()
        else:
            plt.show()        
