#!/usr/bin/env python3

import matplotlib.pyplot as plt
import simulation_utils as su
import dynamic_baseline_sequential as d
import simulation_common as sc
import os
import sys

filename = '/cnet-succR.txt'
lines=['r*-', 'b*-', 'g*-']
# succr_dicts = {sc.maxflow: {}, sc.speedymurmurs: {}, sc.silentwhispers: {}}
# succr_lists = {sc.maxflow: [], sc.speedymurmurs: [], sc.silentwhispers: []}

def plot_success_ratio(configs, exp, sort_by="routing_algorithm"):
    buckets = {}

    # sort by routing algo
    for c in configs:
        buckets = su.sort_singles_configs(c, buckets, sort_by)

    for feature in buckets:
        for c in buckets[feature]:
            p = sc.get_dynamic_data_path_config(c)

    # convert dict of dicts to dict of lists
    bucket_list = {}
    for feature in buckets:
        bucket_list[feature] = []
        for thing in buckets[feature]:
            bucket_list[feature].append(thing)

    succr_dicts = {}
    succr_lists = {}
    for feature in bucket_list:
        succr_dicts[feature] = {}
        succr_lists[feature] = []

    for feature in bucket_list:
        for c in bucket_list[feature]:
            base = sc.get_output_base_path(c)
            p = sc.get_dynamic_data_path_config(c)
            path = os.getcwd() + '/data/' + base + '/' + p[0] + p[1] + filename
            new_succr_dict = su.convert_kv_file_to_dict(path)
            succr_dicts[feature] = su.merge_dicts(new_succr_dict, succr_dicts[feature])

    for feature in buckets:
        succr_lists[feature] = su.dict_to_list(succr_dicts[feature], 801)

    #%config InlineBackend.figure_format ='retina'
    plt.axis([0,700,0,1])
    for i, feature in enumerate(buckets):
        if len(succr_lists[feature]) > 0:
            # lines[i]
            # print(i)
            # plt.plot(range(50, 801), su.running_mean(succr_lists[feature], 50), lines[i], markersize=1, linewidth=1, label=feature)
            plt.plot(range(50, 801), su.running_mean(succr_lists[feature], 50), markersize=1, linewidth=1, label=feature)
    # if len(succr_lists[sc.silentwhispers]) > 0:
    #     plt.plot(range(50, 801), su.running_mean(succr_lists[sc.silentwhispers], 50), 'r*-', markersize=1, linewidth=1, label='SilentWhispers')
    # if len(succr_lists[sc.speedymurmurs]) > 0:
    #     plt.plot(range(50, 801), su.running_mean(succr_lists[sc.speedymurmurs], 50), 'b*-', markersize=1, linewidth=1, label='SpeedyMurmurs')
    # if len(succr_lists[sc.maxflow]) > 0:
    #     plt.plot(range(50, 801), su.running_mean(succr_lists[sc.maxflow], 50), 'g*-', markersize=1, linewidth=1, label='Ford-Fulkerson')
    #matplotlib.pyplot.plot(range(50, 801), running_mean(sm_succr_list, 50), 'b*-', markersize=1, linewidth=1, label='SpeedyMurmurs')
    plt.legend(loc=(0.8, 0.5), scatterpoints=10)
    plt.savefig(exp + '-success-ratio-over-time.png', dpi=300)


if __name__ == "__main__":
    modulename = sys.argv[1]
    new_module = __import__(modulename)
    configs = new_module.generate_configs()
    # plot_success_ratio(configs, modulename, "routing_algorithm")
    plot_success_ratio(configs, modulename, "concurrent_transactions_count")
