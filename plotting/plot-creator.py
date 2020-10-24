import sys
import yaml
import time
import os
import json
import copy
import shutil

if len(sys.argv) < 3:
    print('Usage: python3 plot-creator.py [path_to_plot_templates.yml] [path_to_plot_data.yml]')
    exit(1)

def get_file_contents(filepath):
    with open(filepath) as file:
        contents = yaml.load(file, Loader=yaml.FullLoader)
        return contents

# Has information about the different plots needed
plots = get_file_contents(sys.argv[1])

# Has information about where to get the data for each line for the plots above
plot_line_details = get_file_contents(sys.argv[2])

# Create a directory to store the plot yml files in
dir_name = f'{plot_line_details["plotname"].replace(";", "").replace("/", "_").replace(" ", "-")}-{int(time.time())}'

# Get the different plots required
ignore_keys = []
if 'ignore_keys' in plots:
    ignore_keys = plots['ignore_keys']

plot_data = {}
for plot in plots["plot_params"]:
    p = plot['plot']
    filename = f'{dir_name}/{p["ylabel"].replace(" ", "-")}-vs-{p["xlabel"].replace(" ", "-")}'
    plot_data[filename] = {}
    # write all keys from p into plot_data
    for key in p:
        if key not in ignore_keys:
            plot_data[filename][key] = p[key]
    # TODO: assumes no {each_step}
    pld_copy = copy.deepcopy(plot_line_details)
    for key in pld_copy:
        if key == 'lines':
            # Add filename to each of the line 
            for i in range(0, len(pld_copy[key])):
                pld_copy[key][i]['line']['files'] = []
                pld_copy[key][i]['line']['files'].append(p['filename'])
        plot_data[filename][key] = pld_copy[key]


# Create a directory to store the plot yml files in
os.mkdir(dir_name)

# Copy template to new dir
shutil.copy(sys.argv[2], dir_name + '/template.yml')

for filename in plot_data:
    with open(f'{filename}.yml', 'w') as file:
        yaml.dump(plot_data[filename], file)

print(f'{dir_name}')
