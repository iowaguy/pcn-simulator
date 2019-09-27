#!/bin/bash

for epoch in 10 20 30 50 100; do
    for i in `seq 0 2`; do
	printf "epoch: $epoch\nstep: $i\n"
	java -cp ff-variable-epoch-jars/pcn-simulator-1.0-SNAPSHOT-jar-with-dependencies-${epoch}.jar treeembedding.tests.Dynamic 0 0 $i 
    done
done

