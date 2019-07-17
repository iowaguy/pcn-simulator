#!/bin/bash

usage() {
  echo "$0 [zip | unzip]"
  exit 0
}

zip() {
  algs=( "silentwhispers" "speedymurmurs" )
  for run in `seq 0 8`; do
    for algo in "${algs[@]}"; do
      nohup tar -cvzf "data/dynamic-tiny-${run}-${algo}-3-1-1-concurrent-50.tar.gz" "data/dynamic-tiny-${run}-${algo}-3-1-1-concurrent-50" &
    done
  done
}

unzip() {
  algs=( "silentwhispers" "speedymurmurs" )
  for run in `seq 0 8`; do
    for algo in "${algs[@]}"; do
      nohup tar -xzf "data/dynamic-tiny-${run}-${algo}-3-1-1-concurrent-50.tar.gz" &
    done
  done
}

while :; do
  case "$1" in
    zip)
      zip
      ;;
    unzip)
      unzip
      ;;
    *)
      usage
      ;;
  esac
done
shift $((OPTIND-1))
