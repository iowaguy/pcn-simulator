#!/usr/bin/env python3

import pandas as pd
import os.path
import math

def sanity_check_tx_counts(exp_path, tx_counts):
    if not exp_path and (len(tx_counts) == 0):
        raise Exception("Must provide either transaction count series or experiment path.")
    elif exp_path and (len(tx_counts) == 0):
        tx_counts = get_transactions_per_node(exp_path)

    return tx_counts


def get_top_n_nodes_by_transaction_count(n, exp_path=None, tx_counts=[], roots=3):
    tx_counts = sanity_check_tx_counts(exp_path, tx_counts)

    num_nodes = int(len(tx_counts)/roots)
    lower = 0
    summed_txs = tx_counts[lower:lower + num_nodes] 
    for i in range(roots-1):
        lower += num_nodes
        summed_txs += tx_counts[lower:lower + num_nodes].reset_index(drop=True)

    return summed_txs.nlargest(n, 0)

def get_top_n_nodes_by_tree_depth(n, exp_path, roots=3):
    selected_nodes = pd.DataFrame()
    total_to_select = n
    for i in range(roots):
        
        if total_to_select == 0:
            break
        elif total_to_select < 0:
            raise Error("We should never select more nodes than intended.")

        df = pd.read_csv(exp_path + f"/INITIAL_graph.txt_SPANNINGTREE_{i}",
                         header=None, skiprows=6, sep=';',
                         names=['parent', 'index', 'depth'])

        nodes_to_select_per_tree = int(math.ceil(float(n)/float(roots)))
        # want to select the same number of nodes per tree
        for depth in range(1, df.max()['depth'] + 1):
            nodes_at_current_depth = df[(df.depth == depth)]
            if total_to_select < nodes_to_select_per_tree:
                if len(nodes_at_current_depth) >= total_to_select:
                    select_these = nodes_at_current_depth.sample(total_to_select)
                else:
                    select_these = nodes_at_current_depth
            elif len(nodes_at_current_depth) >= nodes_to_select_per_tree:
                select_these = nodes_at_current_depth.sample(nodes_to_select_per_tree)
            else:
                select_these = nodes_at_current_depth

            selected_nodes = pd.concat([selected_nodes, select_these]) 
            nodes_to_select_per_tree -= len(select_these)
            total_to_select -= len(select_these)
            if total_to_select == 0:
                break

    return selected_nodes.index.values.tolist()

def get_transactions_per_node(exp_path):
    cache_file = exp_path + "/txsPerNodeSummedCache.txt"

    if os.path.isfile(cache_file):
        df = pd.read_csv(cache_file, header=None)
    else:
        # Had to do this in a weird way because each row does not have the
        # same number of columns.
        df = pd.read_csv(exp_path + "/cnet-transactionsPerNode.txt", \
                        header=None, sep='\n', skip_blank_lines=False)

        df = df[0].str.split('\t', expand=True)
        df = df.apply(lambda x: len(x) - x.isnull().sum(), axis='columns').to_frame()
        # Cache the calculation so we don't have to do it again later, it can
        # take awhile.
        df.to_csv(cache_file, index=False, header=False)

    return df
