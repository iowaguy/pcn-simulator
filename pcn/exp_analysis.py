#!/usr/bin/env python3

import pandas as pd

def sanity_check_tx_counts(exp_path, tx_counts):
    if not exp_path and (len(tx_counts) == 0):
        raise Exception("Must provide either transaction count series or experiment path.")
    elif exp_path and (len(tx_counts) == 0):
        tx_count = get_transactions_per_node(exp_path)

    return tx_count


def get_top_n_nodes_by_transaction_count(n, exp_path=None, tx_counts=[], roots=3):
    tx_counts = sanity_check_tx_counts(exp_path, tx_counts)

    num_nodes = int(len(tx_counts)/roots)
    lower = 0
    summed_txs = tx_counts[lower:lower + num_nodes] 
    for i in range(roots-1):
        lower += num_nodes
        summed_txs += tx_counts[lower:lower + num_nodes].reset_index(drop=True)

    return summed_txs.nlargest(n, 0)


def get_transactions_per_node(exp_path):
    # Had to do this in a weird way because each row does not have the
    # same number of columns.
    df = pd.read_csv(exp_path + "/cnet-transactionsPerNode.txt", \
                     header=None, sep='\n', skip_blank_lines=False)

    df = df[0].str.split('\t', expand=True)
    return df.apply(lambda x: len(x) - x.isnull().sum(), axis='columns').to_frame()
