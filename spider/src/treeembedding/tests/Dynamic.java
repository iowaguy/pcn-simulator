package treeembedding.tests;

import gtna.data.Series;
import gtna.metrics.Metric;
import gtna.networks.Network;
import gtna.networks.util.ReadableFile;
import gtna.util.Config;
import treeembedding.credit.CreditOptimal;
import treeembedding.credit.PaymentNetwork;
import treeembedding.credit.RoutingAlgorithmTypes;

public class Dynamic {

  /**
   * @param args 0: run 1: config (0- SilentWhispers, 7- SpeedyMurmurs, 10-MaxFlow) 2: steps
   *             previously completed
   */
  public static void main(String[] args) {
    // General parameters
    Config.overwrite("SKIP_EXISTING_DATA_FOLDERS", "false");
    String results = "data/";
    Config.overwrite("MAIN_DATA_FOLDER", results);
    String path = "../abbreviated-data/finalSets/dynamic/";

    // set up arguments
    int numPaths = 4;
    int run = 100;
    String config = args[0];
    int step = 0;
    double epoch = Double.parseDouble(args[1]);
    // ripple dataset 165.55245497208898*1000)
    String graphName = args[2];
    String transactionFile = args[3];
    double deadline = 3000000;
    String timeSeriesDirName = null;
    double txnDelay = 0.5;
    if (args.length >= 5)
      timeSeriesDirName = args[4];
    if (args.length >= 6)
      deadline = Double.parseDouble(args[5]);
    if (args.length >= 7)
      txnDelay = Double.parseDouble(args[6]);
    if (args.length >= 8)
      numPaths = Integer.parseInt(args[7]);


    // decide prefixes and algorithm names
    String prefix;
    String name = null;
    boolean optimize = false;
    RoutingAlgorithmTypes alg = RoutingAlgorithmTypes.MAXFLOW;
    switch (config) {
      case "SWP":
      case "SW":
        prefix = config;
        name = "SW-P";
        alg = RoutingAlgorithmTypes.SILENTWHISPERS;
        break;
      case "SH":
      case "SHP":
        prefix = config;
        name = "SH-P";
        alg = RoutingAlgorithmTypes.SHORTEST;
        break;
      case "SM":
      case "SMP":
        prefix = config;
        name = "SM-P";
        alg = RoutingAlgorithmTypes.SPEEDYMURMURS;
        break;
      case "BA":
        prefix = config;
        name = "BA-P";
        alg = RoutingAlgorithmTypes.BALANCEAWARE;
        break;
      case "BAO":
        prefix = config;
        name = "BAO-P";
        alg = RoutingAlgorithmTypes.BALANCEAWARE;
        optimize = true;
        break;
      case "HE":
      case "HEP":
        prefix = config;
        name = "HE-P";
        alg = RoutingAlgorithmTypes.HEURISTIC;
        break;
      case "HEO":
        prefix = config;
        name = "HE-P";
        alg = RoutingAlgorithmTypes.HEURISTIC;
        optimize = true;
        break;
      case "MF":
      case "MFP":
        prefix = config;
        name = "MF-P";
        alg = RoutingAlgorithmTypes.MAXFLOW;
        break;
      default:
        throw new IllegalArgumentException("Routing algoithm not supported");
    }

    // paths for graphs and txn dataset
    String graph, trans, newlinks;
    graph = path + graphName;
    trans = path + transactionFile;
    newlinks = null;

    // set up params
    //String creditString = graphName.substring(graphName.indexOf('_') + 1,
    //        graphName.indexOf('.'));
    String creditString = "1";
    double credit = Double.parseDouble(creditString);
    System.out.println("Num paths" + numPaths);
    alg.setParams(credit, transactionFile, numPaths);


    switch (config) {
      // circuit based approaches
      case "SW":
      case "SM":
      case "MF":
      case "SH":
      case "HE":
        runPaymentNetwork(graph, trans, name + (step + 1), newlinks, epoch, run,
                timeSeriesDirName, alg, false, deadline, txnDelay, optimize);
        break;

      // packet based approaches
      case "SWP":
      case "SMP":
      case "MFP":
      case "BA":
      case "BAO":
      case "SHP":
      case "HEO":
      case "HEP":
        runPaymentNetwork(graph, trans, name + (step + 1), newlinks, epoch, run,
                timeSeriesDirName, alg, true, deadline, txnDelay, optimize);
        break;

    }

  }

  public static void runOptimal(String graph, String transList, String name, String links,
                                double epoch, int run, String dirName) {
    Config.overwrite("SERIES_GRAPH_WRITE", "" + true);
    Config.overwrite("SKIP_EXISTING_DATA_FOLDERS", "false");
    Config.overwrite("MAIN_DATA_FOLDER", "data/");
    CreditOptimal o = new CreditOptimal(transList, name,
            0, 0, links, epoch, dirName);
    Network test = new ReadableFile(name, name, graph, null);
    Series.generate(test, new Metric[]{o}, run, run);
  }

  public static void runPaymentNetwork(String graph, String transList, String name, String links,
                                       double epoch, int run, String dirName, RoutingAlgorithmTypes alg, boolean isPacket,
                                       Double deadline, Double txnDelay, boolean optimize) {
    Config.overwrite("SERIES_GRAPH_WRITE", "" + true);
    Config.overwrite("SKIP_EXISTING_DATA_FOLDERS", "false");
    Config.overwrite("MAIN_DATA_FOLDER", "data/");
    PaymentNetwork m = new PaymentNetwork(transList, name,
            0, 0, links, epoch, dirName, alg, isPacket, deadline, txnDelay, optimize);
    Network test = new ReadableFile(name, name, graph, null);
    Series.generate(test, new Metric[]{m}, run, run);
  }

}
