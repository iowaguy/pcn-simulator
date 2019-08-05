#!/bin/bash

for i in `seq 0 8`; do
    java -cp target/pcn-simulator-1.0-SNAPSHOT-jar-with-dependencies.jar treeembedding.tests.Dynamic 0 7 $i 
done

