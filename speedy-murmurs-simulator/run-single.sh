#!/bin/bash

RUN_CONFIG_PATH=$1
#nohup java -cp target/pcn-simulator-1.0-SNAPSHOT-jar-with-dependencies.jar treeembedding.runners.Dynamic data/dynamic-id3-dynamic-baseline-sequential/dynamic-id3-ripple-dynamic-silentwhispers-3-1-1-lat0ms/ &

nohup java -cp target/pcn-simulator-1.0-SNAPSHOT-jar-with-dependencies.jar treeembedding.runners.Dynamic "$RUN_CONFIG_PATH" &
