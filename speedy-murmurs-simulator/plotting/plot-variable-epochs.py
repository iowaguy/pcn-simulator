#!/usr/bin/env python3

import matplotlib.pyplot as plt
import simulation_utils as su
import dynamic_baseline_sequential as d
import simulation_common as sc
import os
import sys

filename = '/cnet-succR.txt'
line_colors = ['b*-', 'g*-', 'r*-', 'c*-', 'm*-']
succr_dicts = {sc.maxflow: {}, sc.speedymurmurs: {}, sc.silentwhispers: {}}
succr_lists = {sc.maxflow: [], sc.speedymurmurs: [], sc.silentwhispers: []}

def plot_success_ratio(configs, exp):
    buckets = {}

    # sort by experiment name
    for c in configs:
        buckets = su.sort_singles_configs(c, buckets, "experiment_name")

    for exp in buckets:
        for c in buckets[exp]:
            p = sc.get_dynamic_data_path_config(c)

    # convert dict of dicts to dict of lists
    bucket_list = {}
    for exp in buckets:
        bucket_list[exp] = []
        for thing in buckets[exp]:
            bucket_list[exp].append(thing)

    for exp in bucket_list:
        for c in bucket_list[exp]:
            base = sc.get_output_base_path(c)
            p = sc.get_dynamic_data_path_config(c)
            path = os.getcwd() + '/data/' + base + '/' + p[0] + p[1] + filename
            new_succr_dict = su.convert_kv_file_to_dict(path)
            succr_dicts[exp] = su.merge_dicts(new_succr_dict, succr_dicts[exp])

    for exp in buckets:
        succr_lists[exp] = su.dict_to_list(succr_dicts[exp], 801)

    i = 0
    for exp in succr_lists:
        plt.plot(range(50, 801), su.running_mean(exp, 50), line_colors[i], markersize=1, linewidth=1, label=f'Ford-Fulkerson-{exp}')
        i += 1
    #matplotlib.pyplot.plot(range(50, 801), running_mean(sm_succr_list, 50), 'b*-', markersize=1, linewidth=1, label='SpeedyMurmurs')
    plt.legend(loc=(0,1), scatterpoints=10)
    plt.savefig(exp + '-success-ratio-over-time.png')


if __name__ == "__main__":
    modulename = sys.argv[1]
    new_module = __import__(modulename)
    configs = new_module.generate_configs()
    plot_success_ratio(configs, modulename)
