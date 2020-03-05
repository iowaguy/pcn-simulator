# Generates a plot-common.yml file from previously created plot.yml files

import os
import sys
import yaml
import time

dir_path = sys.argv[1]
skip_keys = ['lines', 'base', 'plotname']
plots = []

def get_file_contents(file_name):
    with open(file_name, 'r') as file:
        return yaml.load(file, Loader=yaml.FullLoader)

# Read all the files from the above dir
file_names = os.listdir(dir_path)
for file_name in file_names:
    plot = {}
    contents = get_file_contents(f'{dir_path}/{file_name}')
    for key in contents:
        if key not in skip_keys:
            plot[key] = contents[key]
    plot['filename'] = file_name
    plots.append({'plot': plot})

# write these into a file 
plot_common_file_name = f'plot-common-{int(time.time())}.yml'
with open(plot_common_file_name, 'w') as file:
    yaml.dump({'plot_params': plots, 'ignore_keys': ['filename']}, file)

print(f'File: {plot_common_file_name}')


