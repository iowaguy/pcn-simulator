#!/bin/bash

EXP_PATH="/Users/ben/workspace/credit-networks/data/dynamic-id68-47/dynamic-id68-synthetic-random-nodes-10000-txs-pareto-100000-scalefree2-mult-0.5-prob-0.5-speedymurmurs-3-1-1-lat1ms-concurrent-10000-arrivalDelay0ms-griefing_success-10000ms/READABLE_FILE_SM-P0-10000/0/CREDIT_NETWORK-SM-P0-1.0-TREE_ROUTE_TDRAP-true-false-3-0.002-RANDOM_PARTITIONER-1"
DATASET_PATH="/Users/ben/workspace/credit-networks/pcn-topologies/datasets/id68-synthetic-random-nodes-10000-txs-pareto-100000-scalefree2-mult-0.5-prob-0.5"

exec ./plot_tree_stats --tree_stats --exp_path "${EXP_PATH}" --dataset_path "${DATASET_PATH}"
# exec ./plot-txs-per-node --all --exp_path "${EXP_PATH}" --dataset_path "${DATASET_PATH}"
