for i in $(ls plotting/*.yml); do echo $i && ./plot-n-files.py $i; done
