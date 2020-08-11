#!/usr/bin/env python3

import argparse
import matplotlib.pyplot as plt
from typing import List, Dict
from pathlib import Path
import pandas as pd

def plot_centrality(path, filename):
    plt.figure()
    df = pd.read_csv(path + '/' + filename, header=None, delim_whitespace=True)
    df.plot(label='point dominance')

    axes = plt.gca()
#    axes.set_xlim([config.get('xmin'), config.get('xmax')])
#    axes.set_ylim([config.get('ymin'),config.get('ymax')])
    #axes.xaxis.set_major_locator(ticker.MultipleLocator(10000))
    #axes.xaxis.tick_top()

#    legendx = config['legend_loc'][0]
#    legendy = config['legend_loc'][1]
    plt.xlabel("Nodes")
    plt.ylabel("Point dominance")
#    plt.legend(loc=(legendx, legendy), scatterpoints=10)
    p = path + '/' + "point_dominance.png"
    plt.savefig(p, dpi=300)
    print(p)

# plot the distribution of credit on links
def plot_credit_on_links(base: str, fname: str):
    credit = []
    with open(base + '/' + fname, 'r') as f:
        i = 0
        for line in f:
            i += 1
            # skip header
            if i < 5: continue

            # strip "\n" and split line at space
            link = line.rstrip().split(" ")
            # epoch = link[0]
            # node_id = link[1]
            # min_credit = link[2]
            # current_credit = link[3]
            max_credit = float(link[4])
            if max_credit > 0:
                credit.append(max_credit)


    plt.xlabel("Initial balance")
    plt.ylabel("Number of channels")
    # plt.title("Distribution of credit across links", {"wrap":True})

    # print(credit[:100])
    plt.hist(credit, bins=100, range=(0,6000))
    # plt.hist(credit, bins=50)
    p = Path(fname).stem
    new_path = base + '/' + p + '_CREDIT_LINKS.png'
    plt.savefig(new_path, dpi=300)
    plt.close()
    print(new_path)


# plot the distribution of connections per node
def plot_connections_per_node(base: str, fname: str):
    xaxis = 50
    connections = []
    with open(base + '/' + fname, 'r') as f:
        i = 0
        for line in f:
            i += 1
            # skip header
            if i < 8: continue

            # split line at ";"
            connections.append(len(line.split(";")))

    # plt.axis([0, 50, 0, 50])
    # print(connections)
    # plt.xscale('log')
    plt.xlabel("Connections per node")
    plt.ylabel("Number of nodes")
    # plt.title("Number of node connections", {"wrap":True})

    # plt.hist(connections, bins=50, range=(0,50))
    plt.hist(connections, bins=100, range=(0,50))
    p = Path(fname).stem
    new_path = base + '/' + p + '.png'
    plt.savefig(new_path, dpi=300)
    plt.close()
    print(new_path)


def plot_graph(path: str):
    plot_credit_on_links(path, "topology.graph_CREDIT_LINKS")
    plot_connections_per_node(path, "topology.graph")


def tx_plotter(base: str, fname: str, data_type: int, xlabel: str, ylabel: str, title: str, save_path: str, bins=50, range=None):
    bars = []
    types = {"time":0, "amount":1, "payer":2, "payee":3}
    link_val = types.get(data_type)
    with open(base + '/' + fname, 'r') as f:
        i = 0
        for line in f:
            # strip "\n" and split line at space
            link = line.rstrip().split(" ")

            # time = link[0]
            # amount = link[1]
            # payer = link[2]
            # payee = link[3]

            val = link[link_val]
            bars.append(float(val))

    plt.xlabel(xlabel)
    plt.ylabel(ylabel)
    # plt.title(title, {"wrap":True})

    # print(credit[:100])
    # print(bars[:20])
    plt.hist(bars, bins=bins, range=range)
    p = Path(fname).stem
    new_path = base + '/' + p + save_path
    plt.savefig(new_path, dpi=300)
    plt.close()
    print(new_path)

def tx_party_plotter(base: str, fname: str, data_type: int, xlabel: str, ylabel: str, title: str, save_path: str, nodes: int, bins=50, ranges=None):
    bars = [0 for i in range(nodes)]

    types = {"time":0, "amount":1, "payer":2, "payee":3}
    link_val = types.get(data_type)
    with open(base + '/' + fname, 'r') as f:
        i = 0
        for line in f:
            # strip "\n" and split line at space
            link = line.rstrip().split(" ")

            # time = link[0]
            # amount = link[1]
            # payer = link[2]
            # payee = link[3]

            try:
                val = int(link[link_val])
                bars[val] += 1
            except Exception:
                print(link_val)

            # bars.append(float(val))


    # this is useful for plotting the ripple dataset
    # plt.axis([0, 120000, 0, 100])
    plt.xlabel(xlabel)
    plt.ylabel(ylabel)
    # plt.title(title, {"wrap":True})

    bars.sort(reverse=True)

    plt.bar(range(nodes), bars[:nodes])
    p = Path(fname).stem
    new_path = base + '/' + p + save_path
    plt.savefig(new_path, dpi=300)
    plt.close()
    print(new_path)

def plot_tx_size(base: str, fname: str):
    tx_plotter(base, fname, "amount", "Transaction value, $v_i$", "Number of transactions", "Distribution of transactions values", "_value_dist.png", range=(0,100))


def plot_tx_payer_dist(base: str, fname: str, nodes: int):
    tx_party_plotter(base, fname, "payer", "Transaction sender, $s_i$", "Number of transactions", "Distribution of transactions across senders", "_payer_dist.png", nodes)


def plot_tx_payee_dist(base: str, fname: str, nodes: int):
    tx_party_plotter(base, fname, "payee", "Transaction recipient, $r_i$", "Number of transactions", "Distribution of transactions across receivers", "_payee_dist.png", nodes)

def plot_tx_pair_dist(base: str, fname: str, nodes: int):
    xlabel = "Pair ID, $(s_i, r_i)$"
    ylabel = "Number of transactions"
    save_path = "_pair_dist.png"
    bars = {}
    with open(base + '/' + fname, 'r') as f:
        i = 0
        for line in f:
            # strip "\n" and split line at space
            link = line.rstrip().split(" ")

            # time = link[0]
            # amount = link[1]
            # payer = link[2]
            # payee = link[3]

            try:
                sender = int(link[2])
                recipient = int(link[3])
                pair = (sender,recipient)
                new_count = bars.get(pair, 0) + 1
                bars[pair] = new_count
            except Exception as e:
                # print((sender,recipient))
                print(e)

            # bars.append(float(val))

    pair_counts = list(bars.values())
    plt.xlabel(xlabel)
    plt.ylabel(ylabel)

    pair_counts.sort(reverse=True)

    plt.axis([0, 120000, 0, 100])
    # if less that 120000, pad with zeros
    xwidth = 120000
    pair_counts += [0] * (xwidth - len(pair_counts))

    plt.bar(range(xwidth), pair_counts[:xwidth])
    p = Path(fname).stem
    new_path = base + '/' + p + save_path
    plt.savefig(new_path, dpi=300)
    plt.close()
    print(new_path)

def get_num_nodes(base: str, fname: str):
    with open(base + '/' + fname, 'r') as fp:
        for i, line in enumerate(fp):
            if i == 3:
                return int(line)

# plots:
# - the distribution of transaction size
# - the distribution of payers
# - the distribution of payees
def plot_txs(path: str):
    nodes = get_num_nodes(path, "topology.graph")
    plot_tx_payee_dist(path, "transactions.txt", nodes)
    plot_tx_payer_dist(path, "transactions.txt", nodes)
    plot_tx_pair_dist(path, "transactions.txt", nodes)
    plot_tx_size(path, "transactions.txt")
    plot_centrality(path, "central_point_dominances.txt")

if __name__ == "__main__":
    # take in a cli flag that indicates what kind of file will be provided:
    # transactions, topology
        # parse arguments
    parser = argparse.ArgumentParser()
    parser.add_argument('-base', required=True, help='The directory of the GTNA files')
    args = parser.parse_args()

    plot_graph(args.base)
    plot_txs(args.base)
