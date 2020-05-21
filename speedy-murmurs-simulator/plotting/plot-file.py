#!/usr/bin/env python3

import sys
sys.path.insert(1, '../')

import matplotlib.pyplot as plt
import simulation_utils as su
import yaml
from typing import List, Dict
from pathlib import Path
import pandas as pd
import math
import numpy as np

def plot_experiments(config):
    # dfs = []
    plt.figure()
    basepath = config.get('base', '')

    for line in config['lines']:
        l = line['line']
        filebase = l['base']
        if len(l['files']) == 1:
            path = basepath + '/' + filebase + '/' + l['files'][0]
            new_df = pd.read_csv(path, header=None, delim_whitespace=True)
            new_df[1].rolling(window=config.get('running_avg',1)).mean().plot(label=l['name'])
        else:
            print(l['name'])
            # merge files
            df = pd.DataFrame()
            for f in l['files']:
                path = basepath + '/' + filebase + '/' + f
                new_df = pd.read_csv(path, header=None, delim_whitespace=True)
                
                #dfs.append(new_df[np.isreal(new_df[0])])
                df = pd.concat([df, new_df[(new_df[1].notnull())]])
                #print(new_df)

            df[1].rolling(window=config.get('running_avg',1)).mean().plot(label=l['name'])
            #new_df[1].rolling(window=config.get('running_avg',1)).mean().plot(label=l['name'])
#            print(df)

    p = Path(sys.argv[1])
    d = p.parts[-2]
    f = p.stem

    axes = plt.gca()
    axes.set_xlim([config.get('xmin'), config.get('xmax')])
    axes.set_ylim([config.get('ymin'),config.get('ymax')])

    legendx = config['legend_loc'][0]
    legendy = config['legend_loc'][1]
    plt.xlabel(config['xlabel'])
    plt.ylabel(config['ylabel'])
    plt.title(config.get('plotname',""), {"wrap":True})
    plt.legend(loc=(legendx, legendy), scatterpoints=10)
    plt.savefig(d + '/' + f + '.png', dpi=300)

def plot_experiments_cumulative(config):
    if not config.get("cumsum", False):
        return

    # dfs = []
    plt.figure()
    basepath = config.get('base', '')

    for line in config['lines']:
        l = line['line']
        filebase = l['base']
        for f in l['files']:
            path = basepath + '/' + filebase + '/' + f
            new_df = pd.read_csv(path, header=None, delim_whitespace=True)
            # dfs.append(new_df)
            #ser = pd.Series(np.random.normal(size=1000))
            #ser.hist(label=l['name'], cumulative=True, density=1, bins=100)
            #new_df[1].rolling(window=config.get('running_avg',1)).mean()
            #new_df[1].hist(histtype='step', label=l['name'], cumulative=True, density=1, bins=100)
            new_df[1].cumsum().plot(label=l['name'])
            # new_df[1].rolling(window=config.get('running_avg',1)).mean().plot(label=l['name'])

    p = Path(sys.argv[1])
    d = p.parts[-2]
    f = p.stem

    axes = plt.gca()
    axes.set_xlim([config.get('xmin'), config.get('xmax')])
    axes.set_ylim([config.get('ymin'),config.get('ymax')])

    legendx = config['legend_loc'][0]
    legendy = config['legend_loc'][1]
    plt.xlabel(config['xlabel'])
    plt.ylabel(config['ylabel'])
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

    plot_experiments(config)
    plot_experiments_cumulative(config)
