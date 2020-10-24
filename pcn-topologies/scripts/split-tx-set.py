#!/usr/bin/env python3


def writeNLines(l, n, i):
    with open(f'transactions-{i}.txt', 'w') as txout:
        first_line = n*(i-1)
        for j in range(first_line, first_line + n):
            txout.write(l[j])

if __name__ == '__main__':
    n = 10
    inputfile = 'transactions.txt'
    with open(inputfile, 'r') as fin:
        lines = fin.readlines()
        lines_per_file = int(len(lines)/n)

        for i in range(1, n+1):
            writeNLines(lines, lines_per_file, i)
