# Steps for running on hood
1. create a virtual env
2. install the following in the env
   3. ipyparallel = "*"
   4. numpy = "*"
   5. matplotlib = "*"
   6. networkx = "*"
   7. yaml = "*"
   8. pyyaml = "*"
9. start 10 engines with IP cluster, this task is memory bound--more than 20 ipengines causes severe thrashing
   10. this can be done by running the script `./sim start`
10. in speedy-murmurs-simulator/ run `mvn clean install`
11. to start simulation, run `./sim attack`
