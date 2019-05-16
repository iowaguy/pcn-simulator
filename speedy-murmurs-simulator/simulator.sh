#!/bin/bash

ENGINES=10

usage() {
    echo "Usage: $0 [-s|-t|-a]" 1>&2
    exit 1
}

stop() {
    ipcluster stop
    echo "ipcluster killed."

    ps -ef | grep attack | awk '{print $2}' | xargs kill -9
    ps -ef | grep java | awk '{print $2}' | xargs kill -9
    echo "attack killed."
    exit 0
}

status() {
    lines=$(ps -ef | grep ipcluster | wc -l)
    if [ $lines -gt 2 ]; then
	echo "ipcluster is running..."
    else
	echo "ipcluster is not running..."
    fi

    lines=$(ps -ef | grep attack | wc -l)
    if [ $lines -gt 2 ]; then
	echo "attack is running..."
    fi

    exit 0
}

while :; do
    case "$1" in
        start)
	    nohup ipcluster start -n ${ENGINES} &>ipcluster.out &
	    exec tailf ipcluster.out            
            ;;
        stop)
	    stop
            ;;
	status)
	    status
	    ;;
        *)
            usage
            ;;
    esac
done
shift $((OPTIND-1))



