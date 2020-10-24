package treeembedding.runners;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.stream.Stream;

import gtna.data.Series;
import gtna.metrics.Metric;
import gtna.networks.Network;
import gtna.networks.util.ReadableFile;
import gtna.util.Config;
import treeembedding.RoutingAlgorithm;
import treeembedding.RunConfig;
import treeembedding.credit.CreditMaxFlow;
import treeembedding.credit.CreditNetwork;
import treeembedding.credit.partioner.Partitioner;
import treeembedding.credit.partioner.RandomPartitioner;

public class Dynamic {
  private static Logger log = LogManager.getLogger();
  //private static final double E = 165.55245497208898;

  /**
   * @param args 0: run 1: config (0- SilentWhispers, 7- SpeedyMurmurs, 10-MaxFlow) 2: steps
   *             previously completed
   */
  public static void main(String[] args) {
    // this is to allow time for a remote debugger to be connected, if necessary
    if (args.length > 1 && args[1].toLowerCase().equals("debug")) {
      try {
        Thread.sleep(5000);
      } catch (InterruptedException e) {
        // do nothing
      }
    }
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

    // General parameters
    Config.overwrite("SKIP_EXISTING_DATA_FOLDERS", Boolean.toString(!runConfig.isForceOverwrite()));
    Config.overwrite("MAIN_DATA_FOLDER", runDirPath);
    Config.overwrite("SERIES_GRAPH_WRITE", Boolean.toString(true));

    RoutingAlgorithm routingAlgorithm = runConfig.getRoutingAlgorithm();
    String prefix = routingAlgorithm.getShortName();
    String trans = runConfig.getBasePath() + "/" + runConfig.getTransactionPath();
    String newlinks = runConfig.getBasePath() + "/" + runConfig.getNewLinksPath();

    String graph;
    String originalTopoFile = runConfig.getBasePath() + "/" + runConfig.getTopologyPath();


    int step = runConfig.getStep();
    if (step == 0) {
      graph = originalTopoFile;
    } else {
      // make sure previous step has completed
      String nodeCountStr = getNodesFromTopoFile(originalTopoFile);
      graph = runDirPath + "READABLE_FILE_" + prefix + "-P" + (step - 1) + "-" + nodeCountStr + "/0/";
      FilenameFilter fileNameFilter = (dir, name) -> name.contains("CREDIT_NETWORK") || name.contains("CREDIT_MAX");
      String[] files = (new File(graph)).list(fileNameFilter);
      if (files == null || files.length == 0) {
        log.error("Missing file: " + graph);
        System.exit(1);
      }

      graph = graph + files[0] + "/graph.txt";
    }


    String name = routingAlgorithm.getShortName() + "-P" + step;

    double epoch = runConfig.getEpochLength();//E * 1000;



    Metric m = null;
    if (routingAlgorithm == RoutingAlgorithm.SILENTWHISPERS ||
            routingAlgorithm == RoutingAlgorithm.SPEEDYMURMURS ||
            routingAlgorithm == RoutingAlgorithm.SPEEDYMURMURS_COLLATERALIZE_TOTAL) {
      log.info(routingAlgorithm.toString());
      int max = 1;
      double req = runConfig.getEpochLength() / 1000 * 2;
      int[] roots = {64, 36, 43};
      Partitioner part = new RandomPartitioner();

      m = new CreditNetwork(trans, name, epoch, routingAlgorithm, req, part, roots, max, newlinks, runConfig);
    } else if (routingAlgorithm == RoutingAlgorithm.MAXFLOW ||
            routingAlgorithm == RoutingAlgorithm.MAXFLOW_COLLATERALIZE ||
            routingAlgorithm == RoutingAlgorithm.MAXFLOW_COLLATERALIZE_TOTAL) {
      log.info(routingAlgorithm.toString());
      m = new CreditMaxFlow(trans, name,
              0, 0, newlinks, epoch, runConfig);
    } else {
      log.error("Unsupported routing algorithm");
      System.exit(1);
    }


    Network net = new ReadableFile(name, name, graph, null);
    Series.generate(net, new Metric[]{m}, 0, 0);
  }

  private static String getNodesFromTopoFile(String topoFilePath) {
    // get number of nodes from topo file
    try (Stream<String> lines = Files.lines(Paths.get(topoFilePath))) {
      Optional<String> op = lines.skip(3).findFirst();
      if (op.isPresent()) {
        return op.get();
      }
    } catch (IOException e) {
      e.printStackTrace();
    }

    return "";
  }
}
