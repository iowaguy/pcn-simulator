#!/bin/bash

DIR=$1
for i in $(ls ${DIR}/*.yml); do
  if [[ "$i" != "${DIR}/template.yml" ]]; then
      echo $i && ./plot-file.py $i;
  fi
done
