#!/usr/bin/env python3

import sys
import matplotlib.pyplot as plt
import simulation_utils as su
import yaml
from typing import List, Dict

def get_plottable_list(line_config: Dict, xs: int, running_avg=None) -> List[float]:
    base = line_config['base']
    full_dict = {}
    for f in line_config['files']:
        path = base + '/' + f
        d = su.convert_kv_file_to_dict(path)
        full_dict = su.merge_dicts(d, full_dict)

    if running_avg:
        return su.running_mean(su.dict_to_list(full_dict, xs), running_avg)
    else:
        return su.dict_to_list(full_dict, xs)

if __name__ == "__main__":
    with open(sys.argv[1], 'r') as c:
        try:
            config = yaml.safe_load(c)
        except yaml.YAMLError as exc:
            print(exc)
            exit()

    xmax = config['xmax']
    xmin = config['xmin']

    running_avg = None
    if 'running_avg' in config:
        running_avg = config['running_avg']
        xmin += config['running_avg']

    plt.axis([xmin, xmax, 0, 1])
    for line in config['lines']:
        l = get_plottable_list(line['line'], xmax+1, running_avg)
        plt.plot(range(xmin, xmax+1), l, markersize=1, linewidth=2, label=line['line']['name'])

    legendx = config['legend_loc'][0]
    legendy = config['legend_loc'][1]
    plt.title(config['plotname'])
    plt.legend(loc=(legendx, legendy), scatterpoints=10)
    plt.savefig(sys.argv[1][:-4]+'.png', dpi=300)
