for i in $(ls *.yml); do echo $i && ./plot-file.py $i; done
