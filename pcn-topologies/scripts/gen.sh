#!/bin/bash

nohup time ./generate_graph.py test.yml > graph-gen.log &

for i in `seq 1 10`; do touch "newlinks-${i}.txt"; done

touch "newlinks.txt"
