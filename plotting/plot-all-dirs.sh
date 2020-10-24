
dirs=$(ls -d */ | grep -v tmp | grep -v plots | grep -v archived)

for d in ${dirs[@]}; do
    echo $d
    ./plot-all.sh $d
done
