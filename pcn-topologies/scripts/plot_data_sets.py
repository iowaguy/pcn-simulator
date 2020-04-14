#!/usr/bin/env python3

import argparse
import matplotlib.pyplot as plt
from typing import List, Dict
from pathlib import Path

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


    plt.xlabel("Credit per link")
    plt.ylabel("Number of links")
    plt.title("Distribution of credit across links", {"wrap":True})

    # print(credit[:100])
    plt.hist(credit, bins=100, range=(0,10000))
    # plt.hist(credit, bins=50)
    p = Path(fname).stem
    plt.savefig(base + '/' + p + '_CREDIT_LINKS.png', dpi=300)
    plt.close()


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
    plt.title("Number of node connections", {"wrap":True})

    # plt.hist(connections, bins=50, range=(0,50))
    plt.hist(connections, bins=100, range=(0,50))
    p = Path(fname).stem
    plt.savefig(base + '/' + p + '.png', dpi=300)
    plt.close()


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
    plt.title(title, {"wrap":True})

    # print(credit[:100])
    # print(bars[:20])
    plt.hist(bars, bins=bins, range=range)
    p = Path(fname).stem
    plt.savefig(base + '/' + p + save_path, dpi=300)
    plt.close()

def tx_party_plotter(base: str, fname: str, data_type: int, xlabel: str, ylabel: str, title: str, save_path: str, bins=50, ranges=None):
    # s = 93502
    s = 100000
    bars = [0 for i in range(s)]

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


    plt.xlabel(xlabel)
    plt.ylabel(ylabel)
    plt.title(title, {"wrap":True})

    # print(credit[:100])
    # print(bars[:20])
    bars.sort(reverse=True)
    # print(bars)
    plt.bar(range(800), bars[:800])
    p = Path(fname).stem
    plt.savefig(base + '/' + p + save_path, dpi=300)
    plt.close()


def plot_tx_size(base: str, fname: str):
    tx_plotter(base, fname, "amount", "Credit per transaction", "Number of transactions", "Distribution of credit across transactions", "_value_dist.png", range=(0,100))


def plot_tx_payer_dist(base: str, fname: str):
    tx_party_plotter(base, fname, "payer", "Transaction sender", "Number of transactions", "Distribution of transactions across senders", "_payer_dist.png")


def plot_tx_payee_dist(base: str, fname: str):
    tx_party_plotter(base, fname, "payee", "Transaction sender", "Number of transactions", "Distribution of transactions across receivers", "_payee_dist.png")


# plots:
# - the distribution of transaction size
# - the distribution of payers
# - the distribution of payees
def plot_txs(path: str):
    plot_tx_payee_dist(path, "transactions.txt")
    plot_tx_payer_dist(path, "transactions.txt")
    plot_tx_size(path, "transactions.txt")

if __name__ == "__main__":
    # take in a cli flag that indicates what kind of file will be provided:
    # transactions, topology
        # parse arguments
    parser = argparse.ArgumentParser()
    parser.add_argument('-base', required=True, help='The directory of the GTNA files')
    args = parser.parse_args()

    plot_graph(args.base)
    plot_txs(args.base)
