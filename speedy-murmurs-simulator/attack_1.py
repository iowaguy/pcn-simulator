import simulation_common as common

# config = '''
# data_set_name: full-{data_set}
# base: ../data/finalSets/static
# topology: ripple-lcc.graph
# link_weights: ripple-lcc.graph_CREDIT_LINKS
# transaction_set: sampleTr-{data_set}.txt
# simulation_type: static
# force_overwrite: false
# routing_algorithm: "{alg}"
# attempts: 1
# trees: 3
# attack_properties:
#     attackers: {attackers}
#     attacker_selection: "random"
#     attack_type: drop_all
# iterations: 1
# '''

config = '''
data_set_name: tiny-{data_set}
base: ../tiny-data/finalSets/static
topology: ripple-lcc.graph
link_weights: ripple-lcc.graph_CREDIT_LINKS
transaction_set: sampleTr-{data_set}.txt
simulation_type: static
force_overwrite: false
routing_algorithm: "{alg}"
attempts: 1
trees: 3
attack_properties:
    attackers: {attackers}
    attacker_selection: "random"
    attack_type: drop_all
iterations: 1
log_level: warn
'''

def generate_configs():
    config_dict_list_srvna_sm = []
    config_dict_list_srvna_sw = []

    for data_set in range(0, 10):
        for alg in [common.speedymurmurs, common.silentwhispers]:
            # for alg in [silentwhispers]:
            for attackers in range(0, 30001, 5000):
                # for attackers in range(30000, 30001, 5000):
                if alg == common.speedymurmurs:
                    config_dict_list_srvna_sm.append(common.parse_config(
                        config.format(data_set=data_set, alg=alg, attackers=attackers)))
                elif alg == common.silentwhispers:
                    config_dict_list_srvna_sw.append(common.parse_config(
                        config.format(data_set=data_set, alg=alg, attackers=attackers)))
    return (config_dict_list_srvna_sm, config_dict_list_srvna_sw)
