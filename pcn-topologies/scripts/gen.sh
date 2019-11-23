#!/bin/bash

nohup time ./generate_graph.py test.yml &

for i in `seq 1 10`; do touch "newlinks-${i}.txt"; done
