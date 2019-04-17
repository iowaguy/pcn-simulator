package treeembedding.tests;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.File;
import java.util.Map;

import gtna.data.Series;
import gtna.metrics.Metric;
import gtna.networks.Network;
import gtna.networks.util.ReadableFile;
import gtna.util.Config;
import treeembedding.RunConfig;
import treeembedding.byzantine.Attack;
import treeembedding.byzantine.AttackType;
import treeembedding.byzantine.AttackerSelection;
import treeembedding.byzantine.ByzantineNodeSelection;
import treeembedding.byzantine.RandomByzantineNodeSelection;
import treeembedding.credit.CreditMaxFlow;
import treeembedding.credit.CreditNetwork;
import treeembedding.credit.partioner.Partitioner;
import treeembedding.credit.partioner.RandomPartitioner;
import treeembedding.treerouting.Treeroute;
import treeembedding.treerouting.TreerouteOnly;
import treeembedding.treerouting.TreerouteSilentW;
import treeembedding.treerouting.TreerouteTDRAP;

public class Static {

	/**
	 * @param args
	 * 0: run (integer 0-19) 
	 * 1: config (0: LM-MUL-PER(SilentWhispers), 1: LM-RAND-PER, 2: LM-MUL-OND, 3:
	 *            LM-RAND-OND, 4: GE-MUL-PER, 5: GE-RAND-PER, 6: GE-MUL-OND, 7:
	 *            GE-RAND-OND (SpeedyMurmurs), 8: ONLY-MUL-PER, 9:
	 *            ONLY-RAND-OND, 10: max flow) 
	 *2. #transaction attempts             
	 *3: #embeddings/trees (integer > 0)
	 * 
	 * 
	 */
	public static void main(String[] args) {
		String runDirPath = args[0] + '/';
		String runConfigPath = runDirPath + "runconfig.yml";

		ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
		mapper.enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS);

		RunConfig runConfig = null;
		try {
			runConfig = mapper.readValue(new File(runConfigPath), RunConfig.class);
		} catch (Exception e) {
			e.printStackTrace();
			return;
		}

		if (runConfig == null) {
			System.out.println("Unable to parse run configuration file");
			return;
		}

		// General parameters
		Config.overwrite("SKIP_EXISTING_DATA_FOLDERS", Boolean.toString(runConfig.isForceOverwrite()));
		Config.overwrite("MAIN_DATA_FOLDER", runDirPath);
		String path = runConfig.getBasePath();

		// iteration
		int iterations = runConfig.getIterations();

		// configuration in terms of routing algorithm 0-10, see below
		int config = runConfig.getRoutingAlgorithm().getId();

		// file of transactions + graph
		String transList = runConfig.getBasePath() + "/" + runConfig.getTransactionPath();
		String graph = runConfig.getBasePath() + "/" + runConfig.getTopologyPath(); //path + "finalSets/static/ripple-lcc.graph";

		// name of experiment;
		String name = "STATIC";

		// epoch, set to 1000
		double epoch = 1000;

		// time between retries
		double tl = 2 * epoch;

		// number of attempts
		int tries = runConfig.getAttempts();

		// no updates
		boolean up = false;

		if (config == 10) {
			// max flow
			CreditMaxFlow m = new CreditMaxFlow(transList, name, tl, tries, up,
					epoch);
			Network network = new ReadableFile(name, name, graph, null);
			Series.generate(network, new Metric[] { m }, iterations, iterations);
		} else {

			// number of embeddings
			int trees = runConfig.getTrees();

			Attack attackProperties = runConfig.getAttackProperties();


			// partition transaction value randomly
			Partitioner part = new RandomPartitioner();
			// file with degree information + select highest degree nodes as
			// roots
			String degFile = path + "/degOrder-bi.txt";
			int[] roots = Misc.selectRoots(degFile, false, trees, iterations);

			Treeroute sW = new TreerouteSilentW();
			Treeroute voute = new TreerouteTDRAP();
			Treeroute only = new TreerouteOnly();

			ByzantineNodeSelection byz = null;
			if (attackProperties != null && attackProperties.getSelection() == AttackerSelection.RANDOM) {
				byz = new RandomByzantineNodeSelection(attackProperties.getNumAttackers());
			}

			// vary dynRepair, multi, routing algo -> 8 poss + 2 treeonly
			// versions
			CreditNetwork silentW = new CreditNetwork(transList, name, epoch,
					sW, false, true, tl, part, roots, tries, up, byz, attackProperties);
			CreditNetwork silentWnoMul = new CreditNetwork(transList, name,
					epoch, sW, false, false, tl, part, roots, tries, up, byz, attackProperties);
			CreditNetwork silentWdyn = new CreditNetwork(transList, name,
					epoch, sW, true, true, tl, part, roots, tries, up, byz, attackProperties);
			CreditNetwork silentWdynNoMul = new CreditNetwork(transList, name,
					epoch, sW, true, false, tl, part, roots, tries, up, byz, attackProperties);
			CreditNetwork vouteMulnoDyn = new CreditNetwork(transList, name,
					epoch, voute, false, true, tl, part, roots, tries, up, byz, attackProperties);
			CreditNetwork voutenoDyn = new CreditNetwork(transList, name,
					epoch, voute, false, false, tl, part, roots, tries, up, byz, attackProperties);
			CreditNetwork vouteMul = new CreditNetwork(transList, name, epoch,
					voute, true, true, tl, part, roots, tries, up, byz, attackProperties);
			CreditNetwork voutenoMul = new CreditNetwork(transList, name,
					epoch, voute, true, false, tl, part, roots, tries, up, byz, attackProperties);
			CreditNetwork treeonly1 = new CreditNetwork(transList, name, epoch,
					only, false, true, tl, part, roots, tries, up, byz, attackProperties);
			CreditNetwork treeonly2 = new CreditNetwork(transList, name, epoch,
					only, true, false, tl, part, roots, tries, up, byz, attackProperties);

			Metric[] m = new Metric[] { silentW, silentWnoMul, silentWdyn,
					silentWdynNoMul, vouteMulnoDyn, voutenoDyn, vouteMul,
					voutenoMul, treeonly1, treeonly2 };
			String[] com = { "SW-PER-MUL", "SW-PER", "SW-DYN-MUL", "SW-DYN",
					"V-PER-MUL", "V-PER", "V-DYN-MUL", "V-DYN", "TREE-ONLY1",
					"TREE-ONLY1" };

			Network network = new ReadableFile(com[config], com[config], graph,
					null);
      Series s = Series.generate(network, new Metric[] { m[config] }, iterations, iterations);
		}

	}

}
