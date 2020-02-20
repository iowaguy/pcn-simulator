#!/bin/bash

DIR=$1
for i in $(ls ${DIR}/*.yml); do
  echo $i && ./plot-file.py $i;
done
