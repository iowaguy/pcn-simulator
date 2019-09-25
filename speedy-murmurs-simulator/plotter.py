#!/usr/bin/env python3

import matplotlib.pyplot as plt
import simulation_utils as su
import dynamic_baseline_sequential as d
import simulation_common as sc
import os
import sys

filename = '/cnet-succR.txt'

succr_dicts = {sc.maxflow: {}, sc.speedymurmurs: {}, sc.silentwhispers: {}}
succr_lists = {sc.maxflow: [], sc.speedymurmurs: [], sc.silentwhispers: []}

def plot_success_ratio(configs, exp):
    buckets = {}

    # sort by routing algo
    for c in configs:
        buckets = su.sort_singles_configs(c, buckets, "routing_algorithm")

    for alg in buckets:
        for c in buckets[alg]:
            p = sc.get_dynamic_data_path_config(c)

    # convert dict of dicts to dict of lists
    bucket_list = {}
    for alg in buckets:
        bucket_list[alg] = []
        for thing in buckets[alg]:
            bucket_list[alg].append(thing)

    for alg in bucket_list:
        for c in bucket_list[alg]:
            base = sc.get_output_base_path(c)
            p = sc.get_dynamic_data_path_config(c)
            path = os.getcwd() + '/data/' + base + '/' + p[0] + p[1] + filename
            new_succr_dict = su.convert_kv_file_to_dict(path)
            succr_dicts[alg] = su.merge_dicts(new_succr_dict, succr_dicts[alg])

    for alg in buckets:
        succr_lists[alg] = su.dict_to_list(succr_dicts[alg], 801)

    #%config InlineBackend.figure_format ='retina'
    plt.axis([0,700,0,1])
    if len(succr_lists[sc.silentwhispers]) > 0:
        plt.plot(range(50, 801), su.running_mean(succr_lists[sc.silentwhispers], 50), 'r*-', markersize=1, linewidth=1, label='SilentWhispers')
    if len(succr_lists[sc.speedymurmurs]) > 0:
        plt.plot(range(50, 801), su.running_mean(succr_lists[sc.speedymurmurs], 50), 'b*-', markersize=1, linewidth=1, label='SpeedyMurmurs')
    if len(succr_lists[sc.maxflow]) > 0:
        plt.plot(range(50, 801), su.running_mean(succr_lists[sc.maxflow], 50), 'g*-', markersize=1, linewidth=1, label='Ford-Fulkerson')
    #matplotlib.pyplot.plot(range(50, 801), running_mean(sm_succr_list, 50), 'b*-', markersize=1, linewidth=1, label='SpeedyMurmurs')
    plt.legend(loc=(0,1), scatterpoints=10)
    plt.savefig(exp + '-success-ratio-over-time.png')


if __name__ == "__main__":
    modulename = sys.argv[1]
    new_module = __import__(modulename)
    configs = new_module.generate_configs()
    plot_success_ratio(configs, modulename)
