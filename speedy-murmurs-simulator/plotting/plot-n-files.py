#!/usr/bin/env python3

import sys
import matplotlib.pyplot as plt
import simulation_utils as su

if __name__ == "__main__":
    plt.axis([0,700,0,1])

    filename = 'cnet-succR.txt'
    for i in range(0, len(sys.argv)):




        plt.plot(range(50, 801), su.running_mean(succr_lists[feature], 50), markersize=1, linewidth=2, label=feature)
    modulename = sys.argv[1]
