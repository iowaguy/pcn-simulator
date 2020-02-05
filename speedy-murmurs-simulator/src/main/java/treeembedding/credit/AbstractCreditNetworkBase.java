package treeembedding.credit;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.PriorityBlockingQueue;

import gtna.graph.Edge;
import gtna.graph.Graph;
import gtna.io.DataWriter;
import gtna.io.graphWriter.GtnaGraphWriter;
import gtna.metrics.Metric;
import gtna.util.Config;
import gtna.util.Distribution;
import gtna.util.parameter.Parameter;
import treeembedding.RoutingAlgorithm;
import treeembedding.RunConfig;
import treeembedding.byzantine.Attack;
import treeembedding.credit.exceptions.TransactionFailedException;

public abstract class AbstractCreditNetworkBase extends Metric {
  Logger log;
  Graph graph;

  private int transactionCount = 0;
  protected Vector<Transaction> transactions; //vector of transactions, sorted by time
  Queue<Transaction> toRetry;
  RunConfig runConfig;
  private final double epoch; //interval for stabilization overhead (=epoch between spanning tree recomputations if !dynRepair)
  Map<String, Distribution> distributions;
  double[] passRoot;
  CreditLinks edgeweights;
  private int networkLatency;
  Set<Integer> byzantineNodes;
  final Attack attack;
  Queue<Edge> zeroEdges;

  int[] getTransactionsPerEpoch() {
    return perEpochMetrics.get(TRANSACTIONS);
  }

  int[] getSuccessesPerEpoch() {
    return perEpochMetrics.get(SUCCESSES);
  }

  int[] getBlockedLinksPerEpoch() {
    return perEpochMetrics.get(BLOCKED_LINKS);
  }

  private int[] getPerEpochMetric(String name) {
    return perEpochMetrics.get(name);
  }

  private Map<String, List<Long>> longMetrics;
  private Map<String, int[]> perEpochMetrics;

  static final String SINGLE_PATHS_DEST_FOUND = "pathSF"; //distribution of single paths, only discovered paths
  static final String SINGLE_PATHS_DEST_NOT_FOUND = "pathSNF"; //distribution of single paths, not found dest
  static final String SINGLE_PATHS = "pathS"; //distribution of single paths
  static final String ATTEMPTS = "trys"; //Distribution of number of trials needed to get through
  static final String MESSAGES_ALL = "mesAll"; //distribution of #messages needed, counting re-transactions as part of transaction
  static final String PATHS_ALL = "pathAll"; //distribution of path length counting re-transactions as one
  static final String RECEIVER_LANDMARK_MESSAGES = "reLand"; //messages receiver-landmarks communication
  static final String LANDMARK_SENDER_MESSAGES = "landSen"; //messages sender-landmarks communication
  static final String MESSAGES = "mes"; //distribution of #messages needed for one transaction trial i.e. each retry counts as a new transaction
  static final String PATH = "path"; //distribution of path length (sum over all trees!)
  static final String PATH_SUCCESS = "pathSucc"; //path length successful transactions
  static final String PATH_FAIL = "pathFail"; //path length failed transactions
  static final String MESSAGES_SUCCESS = "mesSucc"; //messages successful transactions
  static final String MESSAGES_FAIL = "mesFail"; //messages failed transactions
  static final String DELAY = "del"; //distribution of hop delay
  static final String DELAY_SUCCESS = "delSucc"; //distribution of hop delay, successful queries
  static final String DELAY_FAIL = "delFail"; //distribution of hop delay, failed queries
  static final String CREDIT_MAX_FLOW = "CREDIT_MAX_FLOW";
  static final String BLOCKED_LINKS = "BLOCKED_LINKS";
  private static final String SUCCESSES = "SUCCESSES";
  private static final String TRANSACTIONS = "TRANSACTIONS";


  private static final Map<String, String> FILE_SUFFIXES;
  static final Map<String, String> SINGLE_NAMES;

  static {
    FILE_SUFFIXES = new HashMap<>();
    FILE_SUFFIXES.put(MESSAGES, "_MESSAGES");
    FILE_SUFFIXES.put(MESSAGES_ALL, "_MESSAGES_RE");
    FILE_SUFFIXES.put(MESSAGES_SUCCESS, "_MESSAGES_SUCC");
    FILE_SUFFIXES.put(MESSAGES_FAIL, "_MESSAGES_FAIL");
    FILE_SUFFIXES.put(PATH, "_PATH_LENGTH");
    FILE_SUFFIXES.put(PATHS_ALL, "_PATH_LENGTH_RE");
    FILE_SUFFIXES.put(PATH_SUCCESS, "_PATH_LENGTH_SUCC");
    FILE_SUFFIXES.put(PATH_FAIL, "_PATH_LENGTH_FAIL");
    FILE_SUFFIXES.put(RECEIVER_LANDMARK_MESSAGES, "_REC_LANDMARK");
    FILE_SUFFIXES.put(LANDMARK_SENDER_MESSAGES, "_LANDMARK_SRC");
    FILE_SUFFIXES.put(ATTEMPTS, "_TRIALS");
    FILE_SUFFIXES.put(SINGLE_PATHS, "_PATH_SINGLE");
    FILE_SUFFIXES.put(SINGLE_PATHS_DEST_FOUND, "_PATH_SINGLE_FOUND");
    FILE_SUFFIXES.put(SINGLE_PATHS_DEST_NOT_FOUND, "_PATH_SINGLE_NF");
    FILE_SUFFIXES.put(DELAY, "_DELAY");
    FILE_SUFFIXES.put(DELAY_SUCCESS, "_DELAY_SUCC");
    FILE_SUFFIXES.put(DELAY_FAIL, "_DELAY_FAIL");

    SINGLE_NAMES = new HashMap<>();
    SINGLE_NAMES.put(MESSAGES, "_MES_AV");
    SINGLE_NAMES.put(MESSAGES_ALL, "_MES_RE_AV");
    SINGLE_NAMES.put(MESSAGES_SUCCESS, "_MES_SUCC_AV");
    SINGLE_NAMES.put(MESSAGES_FAIL, "_MES_FAIL_AV");
    SINGLE_NAMES.put(PATH, "_PATH_AV");
    SINGLE_NAMES.put(PATHS_ALL, "_PATH_RE_AV");
    SINGLE_NAMES.put(PATH_SUCCESS, "_PATH_SUCC_AV");
    SINGLE_NAMES.put(PATH_FAIL, "_PATH_FAIL_AV");
    SINGLE_NAMES.put(RECEIVER_LANDMARK_MESSAGES, "_REC_LAND_MES_AV");
    SINGLE_NAMES.put(LANDMARK_SENDER_MESSAGES, "_LAND_SRC_MES_AV");
    SINGLE_NAMES.put(SINGLE_PATHS, "_PATH_SINGLE_AV");
    SINGLE_NAMES.put(SINGLE_PATHS_DEST_FOUND, "_PATH_SINGLE_FOUND_AV");
    SINGLE_NAMES.put(SINGLE_PATHS_DEST_NOT_FOUND, "_PATH_SINGLE_NF_AV");
    SINGLE_NAMES.put(DELAY, "_DELAY_AV");
    SINGLE_NAMES.put(DELAY_SUCCESS, "_DELAY_SUCC_AV");
    SINGLE_NAMES.put(DELAY_FAIL, "_DELAY_FAIL_AV");
    SINGLE_NAMES.put(ATTEMPTS, "_ATTEMPTS");
  }

  List<Integer> cAllPath = new ArrayList<>();
  List<List<Long>> pathSs;
  List<List<Long>> pathSsF;
  List<List<Long>> pathSsNF;
  List<List<Integer>> cPerPath;
  double success = 0; // fraction of transactions successful at all
  double success_first; //fraction of transactions successful in first try
  final int numRoots;
  double[] stab; //stabilization overhead over time (in #messages)
  private double[] successRatePerEpoch;
  private double[] averageSuccessfulPathLengthPerEpoch;
  private double[] blockedLinksRatioPerEpoch;
  private double[][] txStartEndTimes;
  private double[] totalBCDPerEpoch;

  Distribution[] pathsPerTree; //distribution of single paths per tree
  Distribution[] pathsPerTreeFound; //distribution of single paths per tree, only discovered paths
  Distribution[] pathsPerTreeNF; //distribution of single paths per tree, not found dest

  private List<Map<String, LinkBCD>> bcdChanges; // changes to total BCD indexed by epoch

  AbstractCreditNetworkBase(String key, Parameter[] parameters, double epoch, int numRoots,
                            String file, RunConfig runConfig) {
    super(key, parameters);
    this.runConfig = runConfig;
    this.numRoots = numRoots;
    LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
    Configuration config = ctx.getConfiguration();
    LoggerConfig loggerConfig = config.getLoggerConfig(LogManager.ROOT_LOGGER_NAME);
    loggerConfig.setLevel(Level.getLevel(runConfig.getLogLevel().toUpperCase()));
    ctx.updateLoggers();  // This causes all Loggers to refetch information from their LoggerConfig.
    this.log = LogManager.getLogger();
    this.epoch = epoch;
    this.transactions = readList(file);
    this.txStartEndTimes = new double[this.transactions.size()][];
    this.networkLatency = runConfig.getNetworkLatencyMs();
    this.attack = runConfig.getAttackProperties();

    // calculate the number of epochs by calculating the epoch of the last transaction
    int numEpochs = calculateEpoch(transactions.get(transactions.size() - 1));

    // epochs are zero indexed, and the previous calculation will only tell us which epoch index the
    // last transaction was in
    numEpochs++;
    perEpochMetrics = new HashMap<>();
    perEpochMetrics.put(SUCCESSES, new int[numEpochs]);
    perEpochMetrics.put(TRANSACTIONS, new int[numEpochs]);
    perEpochMetrics.put(PATH_SUCCESS, new int[numEpochs]);
    perEpochMetrics.put(BLOCKED_LINKS, new int[numEpochs]);

    longMetrics = new ConcurrentHashMap<>(17);
    longMetrics.put(MESSAGES_ALL, new ArrayList<>());
    longMetrics.put(PATHS_ALL, new ArrayList<>());
    longMetrics.put(RECEIVER_LANDMARK_MESSAGES, new ArrayList<>());
    longMetrics.put(LANDMARK_SENDER_MESSAGES, new ArrayList<>());
    longMetrics.put(MESSAGES, new ArrayList<>());
    longMetrics.put(ATTEMPTS, new ArrayList<>());
    longMetrics.put(PATH, new ArrayList<>());
    longMetrics.put(PATH_SUCCESS, new ArrayList<>());
    longMetrics.put(PATH_FAIL, new ArrayList<>());
    longMetrics.put(MESSAGES_SUCCESS, new ArrayList<>());
    longMetrics.put(MESSAGES_FAIL, new ArrayList<>());
    longMetrics.put(SINGLE_PATHS, new ArrayList<>());
    longMetrics.put(SINGLE_PATHS_DEST_FOUND, new ArrayList<>());
    longMetrics.put(SINGLE_PATHS_DEST_NOT_FOUND, new ArrayList<>());
    longMetrics.put(DELAY, new ArrayList<>());
    longMetrics.put(DELAY_SUCCESS, new ArrayList<>());
    longMetrics.put(DELAY_FAIL, new ArrayList<>());

    pathSs = new ArrayList<>(numRoots);
    pathSsF = new ArrayList<>(numRoots);
    pathSsNF = new ArrayList<>(numRoots);
    cPerPath = new ArrayList<>(numRoots);

    for (int i = 0; i < numRoots; i++) {
      pathSs.add(new ArrayList<>());
      pathSsF.add(new ArrayList<>());
      pathSsNF.add(new ArrayList<>());
      cPerPath.add(new ArrayList<>());
      cPerPath.get(i).add(0);
      cPerPath.get(i).add(1);
    }

    // initialize to start with zeros
    cAllPath.add(0);
    cAllPath.add(0);

    this.distributions = new HashMap<>();

    toRetry = new PriorityBlockingQueue<>();

    bcdChanges = new ArrayList<>(numEpochs);
    for (int i = 0; i < numEpochs; i++) {
      bcdChanges.add(new ConcurrentHashMap<>());
    }

    totalBCDPerEpoch = new double[numEpochs];
  }

  void finalizeUpdateWeight(int src, int dst, double weightChange, int epochNumber)
          throws TransactionFailedException {
    LinkBCD bcd = edgeweights.finalizeUpdateWeight(src, dst, weightChange);
    bcdChanges.get(epochNumber).put(bcd.getName(), bcd);
  }

  static double calculateTotalBCD(CreditLinks edgeweights) {
    double totalBCD = 0;
    for (Map.Entry<Edge, LinkWeight> e : edgeweights.getWeights()) {
      totalBCD += e.getValue().getBCD();
    }

    return totalBCD;
  }

  synchronized void incrementPerEpochValue(String name, int currentEpoch) {
    addPerEpochValue(name, 1, currentEpoch);
  }

  synchronized void addPerEpochValue(String name, double value, int currentEpoch) {
    perEpochMetrics.computeIfPresent(name, (k, v) -> {
      v[currentEpoch] += value;
      return v;
    });
  }

  boolean areTransactionsAvailable() {
    return transactionCount < this.transactions.size() || !toRetry.isEmpty();
  }

  Transaction getNextTransaction() {

    // this code is used to delay transaction start times. useful for testing.
    try {
      if (runConfig.getTransactionDelayMs() > 0) {
        Thread.sleep(runConfig.getTransactionDelayMs());
      }
    } catch (InterruptedException e) {
      // ignore
    }

    //0: decide which is next transaction: previous one or new one?
    Transaction nextTransaction = transactionCount < this.transactions.size() ? this.transactions.get(transactionCount) : null;
    Transaction transactionNeedingRetry = toRetry.peek();
    Transaction currentTransaction = null;
    if (nextTransaction != null && (transactionNeedingRetry == null || nextTransaction.time < transactionNeedingRetry.time)) {
      currentTransaction = nextTransaction;
      transactionCount++;
    } else {
      currentTransaction = toRetry.poll();
    }

    return currentTransaction;
  }

  void simulateNetworkLatency() {
    try {
      Thread.sleep(networkLatency);
    } catch (InterruptedException e) {
      // do nothing
    }
  }

  // reset to old weights if failed
  synchronized void transactionFailed(CreditLinks edgeweights, Map<Edge, List<Double>> edgeModifications) {
    if (edgeModifications == null) {
      return;
    }

    // undo the effects of each of the edge modifications individually
    // for each edge modification, undo the update
    for (Map.Entry<Edge, List<Double>> modifications : edgeModifications.entrySet()) {
      for (Double d : modifications.getValue()) {
        int src = modifications.getKey().getSrc();
        int dst = modifications.getKey().getDst();
        try {
          edgeweights.undoUpdateWeight(src, dst, d);
        } catch (TransactionFailedException e) {
          log.error("Unable to undo a failed transaction. Exiting...");
          System.exit(2);
        }
      }
    }
  }

  synchronized void calculateMetrics(TransactionResults results, Transaction currentTransaction, int currentEpoch) {
    incrementPerEpochValue(TRANSACTIONS, currentEpoch);
    if (results.isSuccess()) {
      incrementPerEpochValue(SUCCESSES, currentEpoch);
      addPerEpochValue(PATH_SUCCESS, results.getSumPathLength(), currentEpoch);
    }

    this.txStartEndTimes[currentTransaction.index] = new double[]{currentTransaction.startTime,
            currentTransaction.endTime};

    incrementCount(PATH, results.getSumPathLength());
    incrementCount(RECEIVER_LANDMARK_MESSAGES, results.getSumReceiverLandmarks());
    incrementCount(LANDMARK_SENDER_MESSAGES, results.getSumSourceDepths());
    incrementCount(MESSAGES, results.getSumMessages());
    incrementCount(DELAY, results.getMaxPathLength());
    if (results.isSuccess()) {
      incrementCount(ATTEMPTS, currentTransaction.timesRequeued);
      this.success++;
      if (currentTransaction.timesRequeued == 0) {
        this.success_first++;
      }
      incrementCount(MESSAGES_ALL, currentTransaction.mes);
      incrementCount(PATHS_ALL, currentTransaction.path);
      incrementCount(PATH_SUCCESS, results.getSumPathLength());
      incrementCount(MESSAGES_SUCCESS, results.getSumMessages());
      incrementCount(DELAY_SUCCESS, results.getMaxPathLength());
    } else {
      incrementCount(PATH_FAIL, results.getSumPathLength());
      incrementCount(MESSAGES_FAIL, results.getSumMessages());
      incrementCount(DELAY_FAIL, results.getMaxPathLength());
    }
    for (int j = 0; j < results.getPathLengths().length; j++) {
      int index = 0;
      if (results.getPathLengths()[j] < 0) {
        index = 1;
      }
      int pathLength = Math.abs(results.getPathLengths()[j]);
      if (!isMaxFlow()) {
        incrementIntegerCount(cPerPath.get(j), index);
        incrementIntegerCount(cAllPath, index);
        incrementCount(pathSs.get(j), pathLength);
      }

      incrementCount(SINGLE_PATHS, pathLength);

      if (index == 0) {
        incrementCount(SINGLE_PATHS_DEST_FOUND, pathLength);
        if (!isMaxFlow()) {
          incrementCount(pathSsF.get(j), pathLength);
        }
      } else {
        incrementCount(SINGLE_PATHS_DEST_NOT_FOUND, pathLength);
        if (!isMaxFlow()) {
          incrementCount(pathSsNF.get(j), pathLength);
        }
      }
    }
  }

  private boolean isMaxFlow() {
    return runConfig.getRoutingAlgorithm() == RoutingAlgorithm.MAXFLOW ||
            runConfig.getRoutingAlgorithm() == RoutingAlgorithm.MAXFLOW_COLLATERALIZE ||
            runConfig.getRoutingAlgorithm() == RoutingAlgorithm.MAXFLOW_COLLATERALIZE_TOTAL;
  }

  int calculateEpoch(Transaction t) {
    return (int) Math.floor(t.time / epoch);
  }

  long[] convertListToLongArray(String propName) {
    return convertListToLongArray(longMetrics.get(propName));
  }

  long[] convertListToLongArray(List<Long> list) {
    if (list.isEmpty()) {
      return new long[0];
    }
    return list.stream().mapToLong(l -> l).toArray();
  }

  private void incrementCount(List<Long> values, int index) {
    if (index < values.size()) {
      values.set(index, values.get(index) + 1);
    } else {
      for (int i = values.size(); i < index; i++) {
        values.add(0L);
      }
      values.add(1L);
    }
  }

  private void incrementIntegerCount(List<Integer> values, int index) {
    if (index < values.size()) {
      values.set(index, values.get(index) + 1);
    } else {
      for (int i = 0; i < index; i++) {
        values.add(0);
      }
      values.add(1);
    }
  }

  void incrementCount(String propName, int index) {
    List<Long> values = longMetrics.get(propName);
    incrementCount(values, index);
  }

  boolean safeWriteWithoutIndex(double[][] in, String keyString, String folder) {
    for (double[] doubles : in) {
      if (doubles == null) {
        return false;
      }
    }
    return DataWriter.writeWithoutIndex(in, this.key + keyString, folder);
  }

  boolean writeDataCommon(String folder) {
    boolean succ = true;
    for (String dataKey : distributions.keySet()) {
      if (distributions.get(dataKey).getDistribution() != null) {
        succ &= DataWriter.writeWithIndex(distributions.get(dataKey).getDistribution(),
                this.key + FILE_SUFFIXES.get(dataKey), folder);
      }
    }

    if (this.txStartEndTimes != null) {
      succ &= DataWriter.writeWithoutIndex(this.txStartEndTimes,
              this.key + "_TX_START_END_TIMES", folder);
    }

    if (this.successRatePerEpoch != null) {
      succ &= DataWriter.writeWithIndex(this.successRatePerEpoch,
              this.key + "_SUCC_RATIOS", folder);
    }

    if (this.averageSuccessfulPathLengthPerEpoch != null) {
      succ &= DataWriter.writeWithIndex(this.averageSuccessfulPathLengthPerEpoch,
              this.key + "_AVG_SUCC_PATH_LENGTH_RATIOS", folder);
    }

    if (this.blockedLinksRatioPerEpoch != null) {
      succ &= DataWriter.writeWithIndex(this.blockedLinksRatioPerEpoch,
              this.key + "_BLOCKED_TXS_RATIO", folder);
    }

    if (this.totalBCDPerEpoch != null) {
      succ &= DataWriter.writeWithIndex(this.totalBCDPerEpoch,
              this.key + "_BIDIRECTIONAL_CREDIT_DEPLETION", folder);
    }

    if (Config.getBoolean("SERIES_GRAPH_WRITE")) {
      (new GtnaGraphWriter()).writeWithProperties(graph, folder + "graph.txt");
    }


    return succ;
  }

  private Vector<Transaction> readList(String file) {
    Vector<Transaction> vec = new Vector<>();
    try {
      BufferedReader br = new BufferedReader(new FileReader(file));
      String line;
      int count = 0;
      for (int i = 0; (line = br.readLine()) != null; i++) {
        String[] parts = line.split(" ");
        if (parts.length == 4) {
          Transaction ta = new Transaction(Double.parseDouble(parts[0]),
                  Double.parseDouble(parts[1]),
                  Integer.parseInt(parts[2]),
                  Integer.parseInt(parts[3]),
                  i);
          vec.add(ta);
        }
        if (parts.length == 3) {
          Transaction ta = new Transaction(count,
                  Double.parseDouble(parts[0]),
                  Integer.parseInt(parts[1]),
                  Integer.parseInt(parts[2]),
                  i);
          vec.add(ta);
          count++;
        }
      }
      br.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
    return vec;
  }

  void blockUntilAsyncTransactionCompletes(Future<TransactionResults> res) {
    try {
      if (res != null) {
        res.get();
      }
    } catch (InterruptedException e) {
      log.error("Failed to block until async transactions complete, InterruptedException: " + e.getMessage());
    } catch (ExecutionException e) {
      log.error("Failed to block until async transactions complete, computation threw an exception: " + e.getMessage());
    }
  }


  // this will block until the all results are available
  void blockUntilAsyncTransactionsComplete(Queue<Future<TransactionResults>> pendingTransactions) {
    for (Future<TransactionResults> res : pendingTransactions) {
      blockUntilAsyncTransactionCompletes(res);
    }
  }

  // used for unit tests
  CreditLinks getCreditLinks() {
    return this.edgeweights;
  }

  void calculatePerEpochRatios() {
    this.successRatePerEpoch = new double[getPerEpochMetric(TRANSACTIONS).length];
    this.averageSuccessfulPathLengthPerEpoch = new double[getPerEpochMetric(PATH_SUCCESS).length];
    this.blockedLinksRatioPerEpoch = new double[getPerEpochMetric(TRANSACTIONS).length];
    for (int epochNumber = 0; epochNumber < getPerEpochMetric(TRANSACTIONS).length; epochNumber++) {
      this.successRatePerEpoch[epochNumber] =
              (double) getPerEpochMetric(SUCCESSES)[epochNumber] /
                      (double) getPerEpochMetric(TRANSACTIONS)[epochNumber];
      this.blockedLinksRatioPerEpoch[epochNumber] =
              (double) getPerEpochMetric(BLOCKED_LINKS)[epochNumber] /
                      (double) getPerEpochMetric(TRANSACTIONS)[epochNumber];
      this.averageSuccessfulPathLengthPerEpoch[epochNumber] =
              (double) getPerEpochMetric(PATH_SUCCESS)[epochNumber] /
                      (double) getPerEpochMetric(SUCCESSES)[epochNumber];
      double runningBCDTotal = epochNumber == 0 ? 0 : totalBCDPerEpoch[epochNumber - 1];
      for (Map.Entry<String, LinkBCD> m : bcdChanges.get(epochNumber).entrySet()) {
        runningBCDTotal -= m.getValue().getPrevious();
        runningBCDTotal += m.getValue().getCurrent();
      }
      totalBCDPerEpoch[epochNumber] = runningBCDTotal;
    }
  }

}