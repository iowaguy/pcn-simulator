package treeembedding.runners;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.File;

import gtna.data.Series;
import gtna.metrics.Metric;
import gtna.networks.Network;
import gtna.networks.util.ReadableFile;
import gtna.util.Config;
import treeembedding.RoutingAlgorithm;
import treeembedding.RunConfig;
import treeembedding.byzantine.Attack;
import treeembedding.byzantine.AttackerSelection;
import treeembedding.byzantine.ByzantineNodeSelection;
import treeembedding.byzantine.RandomByzantineNodeSelection;
import treeembedding.credit.CreditMaxFlow;
import treeembedding.credit.CreditNetwork;
import treeembedding.credit.partioner.Partitioner;
import treeembedding.credit.partioner.RandomPartitioner;

public class Static {

  /**
   * @param args 0: run (integer 0-19) 1: config (0: LM-MUL-PER(SilentWhispers), 1: LM-RAND-PER, 2:
   *             LM-MUL-OND, 3: LM-RAND-OND, 4: GE-MUL-PER, 5: GE-RAND-PER, 6: GE-MUL-OND, 7:
   *             GE-RAND-OND (SpeedyMurmurs), 8: ONLY-MUL-PER, 9: ONLY-RAND-OND, 10: max flow) 2.
   *             #transaction attempts 3: #embeddings/trees (integer > 0)
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
    Config.overwrite("SKIP_EXISTING_DATA_FOLDERS", Boolean.toString(!runConfig.isForceOverwrite()));
    Config.overwrite("MAIN_DATA_FOLDER", runDirPath);
    String path = runConfig.getBasePath();

    // iteration
    int iterations = runConfig.getIterations();

    // configuration in terms of routing algorithm 0-10, see below
    RoutingAlgorithm routingAlgorithm = runConfig.getRoutingAlgorithm();
    //int config = runConfig.getRoutingAlgorithm().getId();

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


    if (routingAlgorithm == RoutingAlgorithm.MAXFLOW) {
      // max flow
      CreditMaxFlow m = new CreditMaxFlow(transList, name, tl, tries, up,
              epoch);
      Network network = new ReadableFile(name, name, graph, null);
      Series.generate(network, new Metric[]{m}, iterations, iterations);
    } else {

      // number of embeddings
      int trees = runConfig.getTrees();

      Attack attackProperties = runConfig.getAttackProperties();

      // partition transaction value randomly
      Partitioner part = new RandomPartitioner();

      // file with degree information + select highest degree nodes as roots
      String degFile = path + "/degOrder-bi.txt";
      int[] roots = Misc.selectRoots(degFile, false, trees, iterations);

      ByzantineNodeSelection byz = null;
      if (attackProperties != null && attackProperties.getSelection() == AttackerSelection.RANDOM) {
        byz = new RandomByzantineNodeSelection(attackProperties.getNumAttackers());
      }

      CreditNetwork creditNetwork = new CreditNetwork(transList, name, epoch, routingAlgorithm, tl, part, roots, tries, up, byz, attackProperties, runConfig);

      String[] com = {"SW-PER-MUL", "SW-PER", "SW-DYN-MUL", "SW-DYN",
              "V-PER-MUL", "V-PER", "V-DYN-MUL", "V-DYN", "TREE-ONLY1",
              "TREE-ONLY1"};

      int config = routingAlgorithm.getId();
      Network network = new ReadableFile(com[config], com[config], graph,
              null);
      Series s = Series.generate(network, new Metric[]{creditNetwork}, 0, iterations - 1);
    }

  }

}
