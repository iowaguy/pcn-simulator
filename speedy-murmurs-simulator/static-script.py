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

def run_static(transaction_num, algo, num_attempts, num_trees):
    if algo == 7:
        algo_name = 'Speedy Murmurs'
    else:
        algo_name = 'Silent Whispers'
    print ('\nRunning static sim: Transaction Num: %d; Algorithm: %s; Number of Attempts: %d; Number of trees: %d\n' % (
            transaction_num, algo_name, num_attempts, num_trees))
    args = str(transaction_num) + ' ' + str(algo) + ' ' + str(num_attempts) + ' ' + str(num_trees)
    os.system('java -cp ~/Documents/masters/CS8982-Reading/credit-networks/speedy-murmurs-simulator/out/production/speedy-murmurs-simulator/ treeembedding.tests.Static ' + args)

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
max_transactions = 3
max_trees = 7
max_attempts = 2

def plot_2ab(filename, metric_txt, attempts):
    os_cmd = 'rm {}'.format(filename)
    print(os_cmd)
    os.system(os_cmd)
    for tree in range(1, max_trees):
        entry = {'trees': str(tree)}
        column_names = ['trees']
        create_plot_for_config(filename, tree, attempts, entry, column_names, metric_txt)

# Fig.2a
plot_2ab('fig2a_vals.txt', 'CREDIT_NETWORK_SUCCESS=', 1)
# Fig.2b
plot_2ab('fig2b_vals.txt', 'CREDIT_NETWORK_DELAY_AV=', 1)


