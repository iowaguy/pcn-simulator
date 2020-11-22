
import pandas as pd
import numpy as np
import matplotlib.pyplot as plt

def cdf_num_children(exp_path, trees=3, nodes=10000):
    __tree_cdf(exp_path + "/cnet-numChildren.txt", 'Number of children', trees, nodes)

def cdf_subtree_size(exp_path, trees=3, nodes=10000):
    __tree_cdf(exp_path + "/cnet-subtreeSize.txt", 'Size of subtree', trees, nodes)

def cdf_node_depth(exp_path, trees=3, nodes=10000):
    __tree_cdf(exp_path + "/cnet-nodeDepths.txt", 'Distance from root', trees, nodes)
    
def __tree_cdf(exp_path, xlabel, trees, nodes):
    df = pd.read_csv(exp_path, header=None, delim_whitespace=True)
    # stuff_per_tree = []
    lower = 0
    # cumsum_per_tree = []
    for i in range(trees):
        data_per_tree = df.iloc[lower:lower + nodes]
        # stuff_per_tree.append(df.iloc[lower:lower + nodes])
        total = data_per_tree[1].sum()
        # cumsum_per_tree.append(np.cumsum(np.sort(data_per_tree[1])/total))
        sorted_data = np.sort(data_per_tree[1])
        out = np.array(range(nodes))/float(nodes)
        plt.plot(sorted_data, out)
        lower += nodes
        print("Top 100: " + ", ".join([str(i) for i in sorted_data[9900:]]))

    plt.legend(('Tree 1', 'Tree 2', 'Tree 3'))
    plt.xlabel(xlabel)
    plt.show()
