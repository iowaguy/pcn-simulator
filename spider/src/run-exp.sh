#!/bin/bash

#scheme_code=("SWP" "SMP" "MFP" "HEP")
#scheme_code=("SW" "SM" "MF" "HE" "MFP" "HEP")
#scheme_code=("SH" "MF" "SHP" "SW" "SWP" "SM")
scheme_code=("MF" "SW" "SHP" "HEP" "BA" "SM")
classpath="-cp .:/home/ubuntu/jheat/jheatchart.jar:/opt/gurobi800/linux64/lib/gurobi.jar"
executable="treeembedding.tests.Dynamic"
graphpre="NorthAmerica"
credit=("10000" "30000" "50000" "75000" "100000" "150000") # "25000")
op_credit=("10000" "30000" "50000" "75000" "100000" "150000")

deadline="5"
num_txns="200000"
src_skew="src_skew_"
txn_size="310"
txn_delay="0.5"
op_obj_prefix="/home/ubuntu/lightning_routing/speedy/src/optimal_paths/obj_"

uniform="uniform_size_"
op_uniform="Uniform_"
size=size${txn_size}.0_
op_size=_TxnSize${txn_size}_
op_num_paths=""
num_paths="4"

# if you supply argument run experiments with src_skew
if [[ $# -ge 1 ]] && [[ $1 = "uniform" ]]
then
      src_skew=""
fi

# if you supply argument run experiments with src_skew
if [[ $# -ge 1 ]] && [[ $1 = "ripplesampled" ]]
then
      uniform=""
      op_uniform=""
fi

# ripple is not uniform or skewed src by default
if [[ ${graphpre} = "RippleStaticClean" ]]
then
    uniform=""
    op_uniform=""
    src_skew=""
    credit=("0")
    num_txns="500000"
    size=""
    op_size=""
    num_paths="4"
fi

# ripple dynamic for the small component is not 
if [[ ${graphpre} = "RippleDynSmallComp" ]]
then
    uniform=""
    op_uniform=""
    src_skew=""
    credit=("0")
    op_credit=("30000")
    num_txns="75980"
    size=""
    op_size=""
fi

comparator="RemValue_"

compile () {
   javac treeembedding/credit/RoutingAlgorithmTypes.java
   javac ${classpath} treeembedding/credit/PaymentNetwork.java
   javac ${classpath} treeembedding/tests/Dynamic.java 
}

# run schemes for different credit setups
run_schemes () {
    for index in ${!scheme_code[@]}
    do
        for graph_index in ${!credit[@]}
        do
            if [ ${scheme_code[$index]} = "BA" ] || [ ${scheme_code[$index]} = "HE" ] || [ ${scheme_code[$index]} = "HEP" ] || [ ${scheme_code[$index]} = "BAO" ] 
            then
                op_num_paths=_${num_paths}_
            else
                op_num_paths=""
            fi

            graphname=${graphpre}_${credit[$graph_index]}.0.graph 
            txnname=${graphpre}_${size}${num_txns}_${src_skew}${uniform}Tr.txt 
            
            # output folder name
            op=${scheme_code[$index]}_Deadline${deadline}_${comparator}Delay${txn_delay}_${graphpre}${op_size}                           
            op+=${op_uniform}${src_skew}Credit${op_credit[$graph_index]}_$(( num_txns / 1000 ))K${op_num_paths}
            
            java ${classpath} ${executable} ${scheme_code[$index]} 10 ${graphname} ${txnname} ${op} ${deadline} ${txnDelay}
        done
    done
}


op_num_paths=""
op_obj_filename=""
#plot them for the scheme and credit setups
plot_schemes () {
    cd ../data/visualizations

    for graph_index in ${!credit[@]}
    do
        scheme_string=""
        for index in ${!scheme_code[@]}
        do
            if [ ${scheme_code[$index]} = "BA" ] || [ ${scheme_code[$index]} = "HE" ] || [ ${scheme_code[$index]} = "HEP" ] || [ ${scheme_code[$index]} = "BAO" ] 
            then
                op_num_paths=_${num_paths}_
            else
                op_num_paths=""
            fi

            scheme_string+=${scheme_code[$index]}_Deadline${deadline}_${comparator}Delay${txn_delay}_${graphpre}
            scheme_string+=${op_size}${op_uniform}
            scheme_string+=${src_skew}Credit${op_credit[$graph_index]}_$(( num_txns / 1000 ))K${op_num_paths}
            scheme_string+=" "

        if [[ ${scheme_code[$index]} = "BA" ]]
        then 
            op_obj_filename=${op_obj_prefix}${op_credit[$graph_index]}${graphpre}_${size}
            op_obj_filename+=${num_txns}_${src_skew}${uniform}Tr
            echo ${op_obj_filename}
        fi

        done
        
        echo ${scheme_string}
        result_folder=Deadline${deadline}_${comparator}Delay${txn_delay}_${graphpre}${op_size}${op_uniform}
        result_folder+=${src_skew}Credit${op_credit[$graph_index]}_$(( num_txns / 1000 ))K

        
        python visualize_time_series.py ${result_folder} ${scheme_string} ${op_obj_filename}
    done

    cd -
}

compile
run_schemes
plot_schemes

