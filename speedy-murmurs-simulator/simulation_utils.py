#!/usr/local/bin/python3
import simulation_common as common

## General utility functions and variables

def running_mean(x, N):
    cumsum = numpy.cumsum(numpy.insert(x, 0, 0))
    return (cumsum[N:] - cumsum[:-N]) / float(N)


def extract_kv_pairs_from_singles(singles_path):
    ret = {}
    with open(singles_path, 'r') as f:
        for line in f:
            k, v = line.rstrip().split('=')
            ret[k] = v
    return ret


def extract_from_singles(algo, attempts, trees, key):
    x_vs_key = []
    for tree in range(1, trees + 1):
        data_path = get_static_data_path(algo, tree, attempts) + '/' + common.singles
        singles_pairs = extract_kv_pairs_from_singles(data_path)
        x_vs_key.append(float(singles_pairs[key]))
    return x_vs_key

def extract_from_singles_config_average(config_dict_list, key, sorting_key1=None, sorting_key2=None):
    import os
    import numpy

    x_vs_key = []
    buckets = extract_from_singles_config(config_dict_list, key, sorting_key1, sorting_key2)
    for bucket in buckets:
        vals = []
        for config_dict in buckets[bucket]:
            static_data_path = get_static_data_path_config(config_dict)
            data_path = os.getcwd() + f'/{simulation_common.data_root}/' + static_data_path[0] + static_data_path[1] + common.singles
            singles_pairs = extract_kv_pairs_from_singles(data_path)
            vals.append(float(singles_pairs[key]))
        x_vs_key.append(numpy.mean(vals))

    return x_vs_key

def extract_from_singles_config_stddev(config_dict_list, key, sorting_key1=None, sorting_key2=None):
    import os
    import numpy

    x_vs_key = []
    buckets = extract_from_singles_config(config_dict_list, key, sorting_key1, sorting_key2)
    for bucket in buckets:
        vals = []
        for config_dict in buckets[bucket]:
            static_data_path = get_static_data_path_config(config_dict)
            data_path = os.getcwd() + f'/{simulation_common.data_root}/' + static_data_path[0] + static_data_path[1] + common.singles
            singles_pairs = extract_kv_pairs_from_singles(data_path)
            vals.append(float(singles_pairs[key]))
        x_vs_key.append(numpy.std(vals))

    return x_vs_key

# this will also calculate averages if there are multiple runs
def extract_from_singles_config(config_dict_list, key, sorting_key1=None, sorting_key2=None):
    buckets = {}

    # sort configs into buckets, where each bucket is the same except for the transaction set
    # the key for the buckets is the property identified by sorting keys 1 and 2
    for config_dict in config_dict_list:
        if sorting_key1 and sorting_key2:
            prop = config_dict[sorting_key1][sorting_key2]
        elif sorting_key1:
            prop = config_dict[sorting_key1]
        elif sorting_key2:
            raise Exception("ERROR: This should never be reached, dictionary is malformed")
        else:
            raise Exception("ERROR: Key does not exist in dictionary")

        if prop not in buckets:
            buckets[prop] = []

        buckets[prop].append(config_dict)
        # else:
        #     # if there is no sorting key, then add to list directly
        #     static_data_path = get_static_data_path_config(config_dict)
        #     data_path = os.getcwd() + f'/{simulation_common.data_root}/' + static_data_path[0] + static_data_path[1] + common.singles
        #     singles_pairs = extract_kv_pairs_from_singles(data_path)
        #     x_vs_key.append(float(singles_pairs[key]))
    return buckets


def extract_from_singles_attempts(algo, num_attempts, trees, key):
    x_vs_key = []
    for attempts in range(1, num_attempts + 1):
        data_path = get_static_data_path(algo, trees, attempts) + '/' + common.singles
        singles_pairs = extract_kv_pairs_from_singles(data_path)
        x_vs_key.append(float(singles_pairs[key]))
    return x_vs_key


def convert_kv_file_to_dict(filepath):
    out_dict = {}
    with open(filepath, 'r') as textfile:
        for line in textfile:
            k, v = line.rstrip().split('\t')
            out_dict[int(k)] = float(v)
    return out_dict


def dict_to_list(d, xrange):
    out_list = []
    for i in range(1, xrange):
        if i in d:
            out_list.append(d[i])
        else:
            out_list.append(0)
    return out_list


# repeated values are ignored
def merge_dicts(dict1, dict2):
    data_dict = {}
    for k, v in dict1.items():
        if k in dict2:
            data_dict[k] = dict2[k]
        else:
            data_dict[k] = dict1[k]
    return data_dict


def get_epoch_length(transactions_file):
    transactions_list = []
    for i in range(1, 10):
        transactions_list = read_transactions_file(transactions_file.format(i), transactions_list)

    # sum the time between subsequent transctions
    sum_delta = 0
    for i in range(1, len(transactions_list)):
        sum_delta += transactions_list[i][0] - transactions_list[i - 1][0]
    delta_av = sum_delta / len(transactions_list)
    return (delta_av * 1000, transactions_list)


def read_link_changes_files(link_changes_file):
    link_changes_list = []
    for i in range(1, 10):
        link_changes_list = read_link_changes_file(link_changes_file.format(i), link_changes_list)
    return link_changes_list


def read_link_changes_file(link_changes_file, link_changes_list=[]):
    with open(link_changes_file, 'r') as link_changes:
        count = 0
        for link_change in link_changes:
            lc = link_change.split(" ")
            # time, source, destination, amount
            link_changes_list.append((int(lc[0]), lc[1], lc[2], lc[3]))

        return link_changes_list


def calculate_events_per_epoch(epoch_length, events):
    cur_epoch = 1
    next_epoch_starts = epoch_length

    events_in_current_epoch = 0
    events_per_epoch = {}
    for event in events:
        if event[0] < next_epoch_starts:
            events_in_current_epoch += 1
        else:
            events_per_epoch[cur_epoch] = events_in_current_epoch
            next_epoch_starts += epoch_length
            cur_epoch += 1
            # should be 1 because it needs to include the current transaction
            events_in_current_epoch = 1
    return events_per_epoch


def read_transactions_file(transactions_file, transactions_list=[]):
    with open(transactions_file, 'r') as transactions:
        count = 0
        for transaction in transactions:
            t = transaction.split(" ")
            if len(t) == 4:
                # time, amount, source, destination
                transactions_list.append((int(t[0]), t[1], t[2], t[3]))
            elif len(t) == 3:
                # time, amount, source, destination
                transactions_list.append((count, t[1], t[2], t[3]))
                count += 1

        return transactions_list


def read_graph_file(graph_file):
    g = networkx.Graph()
    with open(graph_file, 'r') as graph_entries:
        count = 0
        for node in graph_entries:
            count += 1
            # skip meta data at beginning of file
            if count < 8:
                print(f"Skipping metadata, line: {count}")
                continue
            elif count > 15:
                print("Done")
                break

            node_id, connections_str = node.split(':')
            destinations = connections_str.split(';')
            print(f"node_id: {node_id}")
            for dest in destinations:
                # print(f'src: {node_id}; dest: {dest}')
                g.add_edge(node_id, dest)

    return g
