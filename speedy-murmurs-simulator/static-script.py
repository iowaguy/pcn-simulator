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
'''

import os

def run_static(transaction_num, algo, num_attempts, num_trees):
    if algo == 7:
        algo_name = 'Speedy Murmurs'
    else:
        algo_name = 'Silent Whispers'
    print ('\nRunning static sim: Transaction Num: %d; Algorithm: %s; Number of Attempts: %d; Number of trees: %d\n' % (
            transaction_num, algo_name, num_attempts, num_trees))
    args = str(transaction_num) + ' ' + str(algo) + ' ' + str(num_attempts) + ' ' + str(num_trees)
    os.system('java -cp ~/Documents/masters/CS8982-Reading/credit-networks/speedy-murmurs-simulator/out/production/speedy-murmurs-simulator/ treeembedding.tests.Static ' + args)

def run_all_static_variations():
    # For now, just one transaction: 0
    transaction_num = 0
    max_trees = 2
    max_attempts = 2
    algo_list = [7, 0];
    for algo in algo_list:
        for attempt in range(2, max_attempts+1):
            for tree_num in range(2, max_trees+1):
                run_static(transaction_num, algo, attempt, tree_num)

# run_all_static_variations()
run_static(0, 7, 1, 2)
run_static(0, 0, 3, 4)
