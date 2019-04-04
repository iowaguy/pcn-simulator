'''
1. Run for trees 1 - 7
2. For all transactions (0 - 19)
3. Attempts: 1 - 10
4. Algo: Speedy Murmursi(7), Silent Whispers(0)

program args: [transaction] [algo] [attempts] [trees]

Data format

Fig a: 
    x -> trees
    y -> success ratio


folder_name = 

Output Details:
- Inside dir: data/static
- Folder names: 
    - Silent Whispers:  READABLE_FILE_SW-PER-MUL-67149
    - Speedy Murmurs :  READABLE_FILE_V-DYN-67149
- Each folder inside the above will have directories numbered from 0-19 for transaction set
- Created Folder name format: 
Eg    : CREDIT_NETWORK-STATIC-1000.0-TREE_ROUTE_SILENTW-false-true-4-2000.0-RANDOM_PARTITIONER-3
Format: CREDIT_NETWORK-STATIC-[epoch_num]-TREE_ROUTE_[algo]-[dr]-[mul]-[trees]-[ri]-RANDOM_PARTITIONER-[num_attempts]
    - epoch_num: 1000.0
    - algo:
        - Silent Whispers: SILENTW
        - Speedy Murmurs : TDRAP
    - dr:
        - Silent Whispers: false 
        - Speedy Murmurs : true 
    - mul:
        - Silent Whispers: true
        - Speedy Murmurs : false 
    - ri: 2000.0
- Finally, find file named: _singles.txt 

So, for SpeedyMurmurs sim run for transaction set 3, attempts: 2 and trees: 4 the data will be at:
data/static/READABLE_FILE_V-DYN-67149/3/CREDIT_NETWORK-STATIC-1000.0-TREE_ROUTE_TDRAP-true-false-4-2000.0-RANDOM_PARTITIONER-3/_singles.txt

Fig 2.a: success ratio to num trees
x-> avg success ratio for all transaction sets
y-> num trees

trees  sr_sw sr_sm
1      0.5   0.9 
2      0.7   0.8 

- for each algo and const attempts
    - loop from 1 - 7 trees
    - look for CREDIT_NETWORK-STATIC-1000.0-TREE_ROUTE_[algo]-[dr]-[mul]-[tree]-2000.0-RANDOM_PARTITIONER-[attempts]/_singles.txt
    - if does not exist -> run static for the config
    - read line that starts with -> CREDIT_NETWORK_SUCCESS and take first value after= and before [space]
    - write into plot file: x-> tree, y-> value from above step
'''


import os
import sys
import _thread
import signal
import sys

def signal_handler(sig, frame):
        print('You pressed Ctrl+C!')
        sys.exit(0)


def get_filename_with_path(algo, tree, attempts):
    root = 'data/static/'
    if algo == 0:
        folder_name = 'READABLE_FILE_SW-PER-MUL-67149'
        algo_dr_mul = 'SILENTW-false-true'
    elif algo == 7:
        folder_name = 'READABLE_FILE_V-DYN-67149'
        algo_dr_mul = 'TDRAP-true-false'
    else:
        print('Error');
        sys.exit();
    return root + folder_name + '/CREDIT_NETWORK-STATIC-1000.0-TREE_ROUTE_' + algo_dr_mul + '-' + str(tree)+ '-2000.0-RANDOM_PARTITIONER-' + str(attempts) + '/_singles.txt'

def get_algo_name(algo):
    if algo == 0:
        return 'sw'
    return 'sm'

# got from cmd arg
class_path = 'bin/'

def run_static(transaction_num, algo, num_attempts, num_trees):
    if algo == 7:
        algo_name = 'Speedy Murmurs'
    else:
        algo_name = 'Silent Whispers'
    print ('\nRunning static sim: Transaction Num: %d; Algorithm: %s; Number of Attempts: %d; Number of trees: %d\n' % (
            transaction_num, algo_name, num_attempts, num_trees))
    args = str(transaction_num) + ' ' + str(algo) + ' ' + str(num_attempts) + ' ' + str(num_trees)
    os.system('java -cp ' + class_path + ' treeembedding.tests.Static ' + args)

def run_for_algo(algo):
    for t_num in range(0, max_transactions):
        for attempt in range(0, max_attempts):
            for tree_num in range(1, max_trees):
                run_static(t_num, algo, attempt, tree_num)

def run_all_static_variations():
    for algo in algo_list:
        # TODO: option to run on different threads for faster completion.
        run_for_algo(algo)

def run_static_for_all_transactions(algo, tree, attempts):
    for t_num in range(0, max_transactions):
        run_static(t_num, algo, attempts, tree)

def write_to_file(filename, column_names, entry):
    if os.path.exists(filename):
        try:
            with open(filename, 'a') as f:
                op_str = '{0:8s} {1:20s} {2:20s}\n'.format(entry[column_names[0]], entry[column_names[1]], entry[column_names[2]])
                f.write(op_str)
        except FileNotFoundError as e:     
            print('Unable to open file: {filename}'.format(filename))
            sys.exit()
    else:
        with open(filename, 'w') as f:
            op_str = '{0:8s} {1:20s} {2:20s}\n'.format(column_names[0], column_names[1], column_names[2])
            f.write(op_str)
        write_to_file(filename, column_names, entry)


def create_plot_for_config(w_filename, tree, attempts, entry, column_names, metric_txt):
    for algo in algo_list:
        filename = get_filename_with_path(algo, tree, attempts)
        try:
            with open(filename, 'r') as singles:
                for line in singles:
                    if line.startswith(metric_txt):
                        entry[get_algo_name(algo)] = line.split()[0].split('=')[1]
                        column_names.append(get_algo_name(algo))
        except FileNotFoundError as e:
            run_static_for_all_transactions(algo, tree, attempts)
            create_plot_for_config(w_filename, tree, attempts, entry, column_names, metric_txt)
    write_to_file(w_filename, column_names, entry)


algo_list = [7, 0]
max_transactions = 1
max_trees = 2
max_attempts = 4

def create_plt(data_filename, plot_filename, title, xlabel, ylabel, xrange, yrange, title_a="", title_b="", show_grid=True, xtic=1, pointstyle="linespoints"):
    with open(plot_filename, 'w') as p:
        p.write('#!/usr/bin/gnuplot -persist\n')
        p.write('set title "{}"\n'.format(title))
        p.write('set xlabel "{}"\n'.format(xlabel))
        p.write('set ylabel "{}"\n'.format(ylabel))
        p.write('set xrange {}\n'.format(xrange))
        p.write('set yrange {}\n'.format(yrange))
        p.write('set pointsize 1\n')
        if show_grid:
            p.write('set grid\n')
        p.write('plot "{0}" using (column(0)):2:xtic({3}) with {4} title "{1}","{0}" using (column(0)):3:xtic({3}) with {4} title "{2}"\n'.format(data_filename, title_a, title_b, xtic, pointstyle))

def plot_2ab(filename, metric_txt, attempts, plot_title, xlabel, ylabel, xrange, yrange, title_a="", title_b=""):
    # Generate file with data points
    data_filename = filename + '.txt'
    # delete previous files
    os_cmd = 'rm {}'.format(filename + '.*')
    os.system(os_cmd)
    for tree in range(1, max_trees):
        entry = {'trees': str(tree)}
        column_names = ['trees']
        create_plot_for_config(data_filename, tree, attempts, entry, column_names, metric_txt)
    # Generate plotting script
    plot_filename = filename + '.plt'
    create_plt(data_filename, plot_filename, plot_title, xlabel, ylabel, xrange, yrange, title_a, title_b)


def plot_2c(filename, metric_txt, tree, plot_title, xlabel, ylabel, xrange, yrange, title_a="", title_b=""):
    # Generate file with data points
    data_filename = filename + '.txt'
    # delete previous files
    os_cmd = 'rm {}'.format(filename + '.*')
    os.system(os_cmd)
    for attempts in range(1, max_attempts):
        entry = {'attempts': str(attempts)}
        column_names = ['attempts']
        create_plot_for_config(data_filename, tree, attempts, entry, column_names, metric_txt)
    # Generate plotting script
    plot_filename = filename + '.plt'
    create_plt(data_filename, plot_filename, plot_title, xlabel, ylabel, xrange, yrange, title_a, title_b)

def plot_3a(output_filename, transactions_file, link_changes_filename):
    epoch_length, transactions = get_epoch_length(transactions_file)
    link_changes = read_link_changes_files(link_changes_filename)


    transactions_per_epoch = calculate_events_per_epoch(epoch_length, transactions)
    link_changes_per_epoch = calculate_events_per_epoch(epoch_length, link_changes)

    data_dict = {}
    for k, v in transactions_per_epoch.items():
        if k in link_changes_per_epoch:
            # if an epoch includes both transactions and link changes, include both in tuple
            data_dict[k] = (v, link_changes_per_epoch[k])
        else:
            # if an epoch only has transactions, then set link changes to zero
            data_dict[k] = (v, 0)

    for k, v in link_changes_per_epoch.items():
        if k in data_dict:
            # if an epoch containing a link change is already in the dict, then don't add it again
            # it will already include the transactions if there were any
            continue
        else:
            # if an epoch with a link change is not in the dict, add it and assume zero transactions
            # this is a safe assumption, because if there were transactions in the epoch, it would have
            # been included in the previous loop
            data_dict[k] = (0, v)

    data_filename = output_filename + '.txt'
    plot_filename = output_filename + '.plt'
    save_data_points(data_filename, data_dict, ["epoch", "transactions", "link-changes"])
    create_plt(data_filename, plot_filename, "Figure 3a", "Epoch Number", "Count", "[0:700]", "[0:25000]", "Transactions", "Set Link", show_grid=False, xtic=100, pointstyle="points")

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

def save_data_points(filename, data_points, column_names):
    with open(filename, 'w') as f:
        op_str = '{0:8s} {1:20s} {2:20s}\n'.format(column_names[0], column_names[1], column_names[2])
        f.write(op_str)
        for key, (v1, v2) in data_points.items():
            op_str = '{0:8s} {1:20s} {2:20s}\n'.format(str(key), str(v1), str(v2))
            f.write(op_str)

def read_transactions_file(transactions_file, transactions_list = []):
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

def read_link_changes_files(link_changes_file):
    link_changes_list = []
    for i in range(1, 10):
        link_changes_list = read_link_changes_file(link_changes_file.format(i), link_changes_list)
    return link_changes_list

def read_link_changes_file(link_changes_file, link_changes_list = []):
    with open(link_changes_file, 'r') as link_changes:
        count = 0
        for link_change in link_changes:
            lc = link_change.split(" ")
            # time, source, destination, amount
            link_changes_list.append((int(lc[0]), lc[1], lc[2], lc[3]))

        return link_changes_list

def get_epoch_length(transactions_file):
    transactions_list = []
    for i in range(1, 10):
        transactions_list = read_transactions_file(transactions_file.format(i), transactions_list)

    # sum the time between subsequent transctions
    sum_delta = 0
    for i in range(1, len(transactions_list)):
        sum_delta += transactions_list[i][0] - transactions_list[i - 1][0]
    delta_av = sum_delta/len(transactions_list)
    return (delta_av * 1000, transactions_list)

def plot_all_static_figs():
    # Fig.2a
    plot_2ab(root + 'fig2a', 'CREDIT_NETWORK_SUCCESS=', 1, 'Fig 2a', 'Trees', 'Success Ratio', "[0:8]", "[0:1]", "SpeedyMurmurs", "SilentWhispers")
    # Fig.2b
    plot_2ab(root + 'fig2b', 'CREDIT_NETWORK_DELAY_AV=', 1, 'Fig 2b', 'Trees', 'Hops(Delay)', "[0:8]", "[0:1]", "SpeedyMurmurs", "SilentWhispers")
    # Fig.2c
    plot_2c(root + 'fig2c', 'CREDIT_NETWORK_SUCCESS=', 3, 'Fig 2c', 'Attempts', 'Success Ratio', "[0:10]", "[0:1]", "SpeedyMurmurs", "SilentWhispers")

def plot_all_dynamic_figs():
    plot_3a(root + 'fig3a', "../data/finalSets/dynamic/jan2013-trans-lcc-noself-uniq-{0}.txt", "../data/finalSets/dynamic/jan2013-newlinks-lcc-sorted-uniq-t{0}.txt")


if  __name__ =='__main__':
    signal.signal(signal.SIGINT, signal_handler)
    if len(sys.argv) > 1:
        class_path = sys.argv[1]
        print('Class path provided: ' + class_path)
    else:
        print('No class path provided, defaulting to bin/')
    root = 'plot/'

    # plot_all_static_figs()
    plot_all_dynamic_figs()
