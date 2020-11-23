#!/usr/bin/env python3

import matplotlib.pyplot as plt
import simulation_utils as su
from typing import List, Dict
from pathlib import Path
import pandas as pd
import math
import numpy as np
import matplotlib.ticker as ticker

def line_plot_from_list(file_list, xlabel, ylabel, x_range=[], y_range=[], legend_labels=[], running_avg=1):
    """
    Given a list of files in the format of the simulator 
    results, plot them against each other
    """

    if not legend_labels:
        legend_labels = range(len(file_list))

    for f in file_list:
        df = pd.read_csv(f, header=None, delim_whitespace=True)
        # import pdb; pdb.set_trace()
        df[1][(df[1].notnull())].rolling(window=running_avg).mean().plot()
        
    axes = plt.gca()

    if x_range:
        axes.set_xlim(x_range)
    if y_range:
        axes.set_ylim(y_range)

    plt.xlabel(xlabel)
    plt.ylabel(ylabel)

    plt.legend(legend_labels)
    plt.tight_layout()    
    plt.show()