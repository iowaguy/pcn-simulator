package treeembedding.tests;

import gtna.data.Series;
import gtna.metrics.Metric;
import gtna.networks.Network;
import gtna.networks.util.ReadableFile;
import gtna.util.Config;
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
		// General parameters
		Config.overwrite("SKIP_EXISTING_DATA_FOLDERS", "false");
		Config.overwrite("MAIN_DATA_FOLDER", "./data/static/");
		String path = "../data/";
		// iteration
		int i = Integer.parseInt(args[0]);
		// configuration in terms of routing algorithm 0-10, see below
		int config = Integer.parseInt(args[1]);
		// file of transactions + graph
		String transList = path + "finalSets/static/sampleTr-" + i + ".txt";
		String graph = path + "finalSets/static/ripple-lcc.graph";
		// name of experiment;
		String name = "STATIC";
		// epoch, set to 1000
		double epoch = 1000;
		// time between retries
		double tl = 2 * epoch;
		// number of attempts
		int tries = Integer.parseInt(args[2]);
		// no updates
		boolean up = false;

		if (config == 10) {
			// max flow
			CreditMaxFlow m = new CreditMaxFlow(transList, name, tl, tries, up,
					epoch);
			Network network = new ReadableFile(name, name, graph, null);
			Series.generate(network, new Metric[] { m }, i, i);
		} else {

			// number of embeddings
			int trees = Integer.parseInt(args[3]);
			// partition transaction value randomly
			Partitioner part = new RandomPartitioner();
			// file with degree information + select highest degree nodes as
			// roots
			String degFile = path + "finalSets/static/degOrder-bi.txt";
			int[] roots = Misc.selectRoots(degFile, false, trees, i);

			Treeroute sW = new TreerouteSilentW();
			Treeroute voute = new TreerouteTDRAP();
			Treeroute only = new TreerouteOnly();

			// vary dynRepair, multi, routing algo -> 8 poss + 2 treeonly
			// versions
			CreditNetwork silentW = new CreditNetwork(transList, name, epoch,
					sW, false, true, tl, part, roots, tries, up); 
			CreditNetwork silentWnoMul = new CreditNetwork(transList, name,
					epoch, sW, false, false, tl, part, roots, tries, up); 
			CreditNetwork silentWdyn = new CreditNetwork(transList, name,
					epoch, sW, true, true, tl, part, roots, tries, up); 
			CreditNetwork silentWdynNoMul = new CreditNetwork(transList, name,
					epoch, sW, true, false, tl, part, roots, tries, up); 
			CreditNetwork vouteMulnoDyn = new CreditNetwork(transList, name,
					epoch, voute, false, true, tl, part, roots, tries, up); 
			CreditNetwork voutenoDyn = new CreditNetwork(transList, name,
					epoch, voute, false, false, tl, part, roots, tries, up); 
			CreditNetwork vouteMul = new CreditNetwork(transList, name, epoch,
					voute, true, true, tl, part, roots, tries, up); 
			CreditNetwork voutenoMul = new CreditNetwork(transList, name,
					epoch, voute, true, false, tl, part, roots, tries, up);
			CreditNetwork treeonly1 = new CreditNetwork(transList, name, epoch,
					only, false, true, tl, part, roots, tries, up); 
			CreditNetwork treeonly2 = new CreditNetwork(transList, name, epoch,
					only, true, false, tl, part, roots, tries, up); 

			Metric[] m = new Metric[] { silentW, silentWnoMul, silentWdyn,
					silentWdynNoMul, vouteMulnoDyn, voutenoDyn, vouteMul,
					voutenoMul, treeonly1, treeonly2 };
			String[] com = { "SW-PER-MUL", "SW-PER", "SW-DYN-MUL", "SW-DYN",
					"V-PER-MUL", "V-PER", "V-DYN-MUL", "V-DYN", "TREE-ONLY1",
					"TREE-ONLY1" };

			Network network = new ReadableFile(com[config], com[config], graph,
					null);
			Series.generate(network, new Metric[] { m[config] }, i, i);
		}

	}

}
