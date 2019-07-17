#!/bin/bash
algs=( "silentwhispers" "speedymurmurs" )
for run in `seq 0 8`; do
  for algo in "${algs[@]}"; do
    nohup tar -cvzf "data/dynamic-tiny-${run}-${algo}-3-1-1-concurrent-50.tar.gz" "data/dynamic-tiny-${run}-${algo}-3-1-1-concurrent-50" &
  done
done
