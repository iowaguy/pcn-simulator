#!/usr/bin/env python3

import sys
sys.path.insert(1, '../')

import matplotlib.pyplot as plt
import simulation_utils as su
import yaml
from typing import List, Dict
from pathlib import Path
import pandas as pd

def get_plottable_list(line_config: Dict, xs: int, running_avg=None, basepath='') -> List[float]:
    base = line_config['base']
    full_dict = {}
    for f in line_config['files']:
        path = basepath + '/' + base + '/' + f
        d = su.convert_kv_file_to_dict(path)
        full_dict = su.merge_dicts(d, full_dict)

    if running_avg:
        return su.running_mean(su.dict_to_list(full_dict, xs), running_avg)
    else:
        return su.dict_to_list(full_dict, xs)

def plot_direct(config):
    xmax = config['xmax']
    xmin = config['xmin']

    ymin = config.get('ymin', 0)
    ymax = config.get('ymax', 1)

    basepath = config.get('base', '')

    running_avg = None
    if 'running_avg' in config:
        running_avg = config['running_avg']
        xmin += config['running_avg']

    plt.axis([xmin, xmax, ymin, ymax])
    for line in config['lines']:
        l = get_plottable_list(line['line'], xmax+1, running_avg, basepath)
        plt.plot(range(xmin, xmax+1), l, markersize=1, linewidth=2, label=line['line']['name'])

    legendx = config['legend_loc'][0]
    legendy = config['legend_loc'][1]
    plt.xlabel(config['xlabel'])
    plt.ylabel(config['ylabel'])
    plt.title(config.get('plotname',""), {"wrap":True})
    plt.legend(loc=(legendx, legendy), scatterpoints=10)
    p = Path(sys.argv[1])
    d = p.parts[-2]
    f = p.stem
    plt.savefig(d + '/' + f + '.png', dpi=300)

def plot_cumsum(config):
    # do cumsum in pandas
    dfs = []
    plt.figure()
    basepath = config.get('base', '')
    if not config.get("cumsum", False):
        return

    for line in config['lines']:
        l = line['line']
        filebase = l['base']
        for f in l['files']:
            path = basepath + '/' + filebase + '/' + f
            new_df = pd.read_csv(path, header=None, delim_whitespace=True)
            dfs.append(new_df)
            new_df[1].cumsum().plot(label=l['name'])

    p = Path(sys.argv[1])
    d = p.parts[-2]
    f = p.stem

    legendx = config['legend_loc'][0]
    legendy = config['legend_loc'][1]
    plt.xlabel(config['xlabel'])
    plt.ylabel(config['ylabel'] + " cumulative")
    plt.title(config.get('plotname',""), {"wrap":True})
    plt.legend(loc=(legendx, legendy), scatterpoints=10)

    plt.savefig(d + '/' + f + '-cumulative.png', dpi=300)


if __name__ == "__main__":
    with open(sys.argv[1], 'r') as c:
        try:
            config = yaml.safe_load(c)
        except yaml.YAMLError as exc:
            print(exc)
            exit()

    plot_direct(config)
    plot_cumsum(config)
