package treeembedding.runners;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FilenameFilter;

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

public class Dynamic {
  private static Logger log = LogManager.getLogger();
  private static final double E = 165.55245497208898;

  /**
   * @param args 0: run 1: config (0- SilentWhispers, 7- SpeedyMurmurs, 10-MaxFlow) 2: steps
   *             previously completed
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
      log.error("Unable to parse run configuration file");
      return;
    }

    runConfig.setRunDirPath(runDirPath);

    // General parameters
    Config.overwrite("SKIP_EXISTING_DATA_FOLDERS", Boolean.toString(!runConfig.isForceOverwrite()));
    Config.overwrite("MAIN_DATA_FOLDER", runDirPath);
    Config.overwrite("SERIES_GRAPH_WRITE", Boolean.toString(true));

    RoutingAlgorithm routingAlgorithm = runConfig.getRoutingAlgorithm();
    String prefix = routingAlgorithm.getShortName();
    String trans = runConfig.getBasePath() + "/" + runConfig.getTransactionPath();
    String newlinks = runConfig.getBasePath() + "/" + runConfig.getNewLinksPath();

    String graph;
    int step = runConfig.getStep();
    if (step == 0) {
      graph = runConfig.getBasePath() + "/" + runConfig.getTopologyPath();
    } else {
      graph = runDirPath + "READABLE_FILE_" + prefix + "-P" + step + "-93502/0/";
      FilenameFilter fileNameFilter = (dir, name) -> name.contains("CREDIT_NETWORK") || name.contains("CREDIT_MAX");
      String[] files = (new File(graph)).list(fileNameFilter);
      if (files == null || files.length == 0) {
        log.error("Missing file");
        System.exit(1);
      }

      graph = graph + files[0] + "/graph.txt";
    }


    String name = routingAlgorithm.getShortName() + "-P" + (step + 1);

    double epoch = E * 1000;

    Attack attackProperties = runConfig.getAttackProperties();
    ByzantineNodeSelection byz = null;
    if (attackProperties != null && attackProperties.getSelection() == AttackerSelection.RANDOM) {
      byz = new RandomByzantineNodeSelection(attackProperties.getNumAttackers());
    }

    Metric m = null;
    if (routingAlgorithm == RoutingAlgorithm.SILENTWHISPERS ||
            routingAlgorithm == RoutingAlgorithm.SPEEDYMURMURS) {
      int max = 1;
      double req = E * 2;
      int[] roots = {64, 36, 43};
      Partitioner part = new RandomPartitioner();

      m = new CreditNetwork(trans, name, epoch, routingAlgorithm, req, part, roots, max, newlinks, byz, attackProperties, runConfig);
    } else if (routingAlgorithm == RoutingAlgorithm.MAXFLOW) {
      m = new CreditMaxFlow(trans, name,
              0, 0, newlinks, epoch);
    } else {
      log.error("Unsupported routing algorithm");
      System.exit(1);
    }


    Network net = new ReadableFile(name, name, graph, null);
    Series.generate(net, new Metric[]{m}, 0, 0);
  }

}