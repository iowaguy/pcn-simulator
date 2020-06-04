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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadLocalRandom;

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
  Map<Metrics, Distribution> distributions;
  double[] passRoot;
  CreditLinks edgeweights;
  private final int networkLatency;
  Set<Integer> byzantineNodes;
  final Attack attack;
  Queue<Edge> zeroEdges;
  private final double[][] txStartEndTimes;

  private final Map<Metrics, List<Long>> longMetrics;
  private final Map<Metrics, int[]> perEpochMetrics;
  private final Map<Metrics, double[]> perEpochDoubleMetrics;

  static final String CREDIT_MAX_FLOW = "CREDIT_MAX_FLOW";

  int[] getTransactionsPerEpoch() {
    return perEpochMetrics.get(Metrics.TRANSACTIONS);
  }

  int[] getSuccessesPerEpoch() {
    return perEpochMetrics.get(Metrics.SUCCESSES);
  }

  int[] getBlockedLinksPerEpoch() {
    return perEpochMetrics.get(Metrics.BLOCKED_LINKS_PER_EPOCH);
  }

  int[] getPerEpochMetric(Metrics name) {
    return perEpochMetrics.get(name);
  }

  double[] getPerEpochDoubleMetric(Metrics name) {
    return perEpochDoubleMetrics.get(name);
  }

  enum Metrics {
    SINGLE_PATHS_DEST_FOUND("_PATH_SINGLE_FOUND", "_PATH_SINGLE_FOUND_AV"), //distribution of single paths, only discovered paths
    SINGLE_PATHS_DEST_NOT_FOUND("_PATH_SINGLE_NF", "_PATH_SINGLE_NF_AV"), //distribution of single paths, not found dest
    SINGLE_PATHS("_PATH_SINGLE", "_PATH_SINGLE_AV"), //distribution of single paths
    ATTEMPTS("_TRIALS", "_ATTEMPTS"), //Distribution of number of trials needed to get through
    MESSAGES_ALL("_MESSAGES_RE", "_MES_RE_AV"), //distribution of #messages needed, counting re-transactions as part of transaction
    PATHS_ALL("_PATH_LENGTH_RE", "_PATH_RE_AV"), //distribution of path length counting re-transactions as one
    RECEIVER_LANDMARK_MESSAGES("_REC_LANDMARK", "_REC_LAND_MES_AV"), //messages receiver-landmarks communication
    LANDMARK_SENDER_MESSAGES("_LANDMARK_SRC", "_LAND_SRC_MES_AV"), //messages sender-landmarks communication
    MESSAGES("_MESSAGES", "_MES_AV"), //distribution of #messages needed for one transaction trial i.e. each retry counts as a new transaction
    PATH("_PATH_LENGTH", "_PATH_AV"), //distribution of path length (sum over all trees!)
    PATH_SUCCESS("_PATH_LENGTH_SUCC", "_PATH_SUCC_AV"), //path length successful transactions
    PATH_FAIL("_PATH_LENGTH_FAIL", "_PATH_FAIL_AV"), //path length failed transactions
    MESSAGES_SUCCESS("_MESSAGES_SUCC", "_MES_SUCC_AV"), //messages successful transactions
    MESSAGES_FAIL("_MESSAGES_FAIL", "_MES_FAIL_AV"), //messages failed transactions
    DELAY("_DELAY", "_DELAY_AV"), //distribution of hop delay
    DELAY_SUCCESS("_DELAY_SUCC", "_DELAY_SUCC_AV"), //distribution of hop delay, successful queries
    DELAY_FAIL("_DELAY_FAIL", "_DELAY_FAIL_AV"), //distribution of hop delay, failed queries
    AVG_SUCCESSFUL_PATH_LENGTH_PER_EPOCH("_AVG_SUCC_PATH_LENGTH_RATIOS"),
    BLOCKED_LINKS_PER_EPOCH,
    BLOCKED_LINKS_RATIO_PER_EPOCH("_BLOCKED_TXS_RATIO"),
    SUCCESSES,
    SUCCESS_RATE,
    SUCCESS_RATE_PER_EPOCH("_SUCC_RATIOS"),
    TRANSACTIONS,
    CREDIT_DEVIATION_PER_EPOCH("_CREDIT_DEVIATION_PER_EPOCH"),
    BCD_PER_EPOCH,
    BCD_NORMALIZED("_BIDIRECTIONAL_CREDIT_DEPLETION"),
    TOTAL_CREDIT_TRANSACTED_PER_EPOCH("_TOTAL_CREDIT_TRANSACTED"),
    TX_START_END_TIME_NS("_START_END_TIME_NS");

    final String fileSuffix;
    final String singleName;

    String getFileSuffix() {
      return this.fileSuffix;
    }

    String getSingleName() {
      return this.singleName;
    }

    Metrics() {
      this.fileSuffix = "";
      this.singleName = "";
    }

    Metrics(String fileSuffix) {
      this.fileSuffix = fileSuffix;
      this.singleName = "";
    }

    Metrics(String fileSuffix, String singleName) {
      this.fileSuffix = fileSuffix;
      this.singleName = singleName;
    }
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



  Distribution[] pathsPerTree; //distribution of single paths per tree
  Distribution[] pathsPerTreeFound; //distribution of single paths per tree, only discovered paths
  Distribution[] pathsPerTreeNF; //distribution of single paths per tree, not found dest

  private final List<Map<String, LinkStats>> linkMetricChanges; // changes to link metrics indexed by epoch and edge name

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
    perEpochMetrics.put(Metrics.SUCCESSES, new int[numEpochs]);
    perEpochMetrics.put(Metrics.TRANSACTIONS, new int[numEpochs]);
    perEpochMetrics.put(Metrics.PATH_SUCCESS, new int[numEpochs]);
    perEpochMetrics.put(Metrics.BLOCKED_LINKS_PER_EPOCH, new int[numEpochs]);

    perEpochDoubleMetrics = new HashMap<>();
    perEpochDoubleMetrics.put(Metrics.BCD_NORMALIZED, new double[numEpochs]);
    perEpochDoubleMetrics.put(Metrics.BCD_PER_EPOCH, new double[numEpochs]);
    perEpochDoubleMetrics.put(Metrics.TOTAL_CREDIT_TRANSACTED_PER_EPOCH, new double[numEpochs]);
    perEpochDoubleMetrics.put(Metrics.CREDIT_DEVIATION_PER_EPOCH, new double[numEpochs]);

    longMetrics = new ConcurrentHashMap<>(17);
    longMetrics.put(Metrics.MESSAGES_ALL, new ArrayList<>());
    longMetrics.put(Metrics.PATHS_ALL, new ArrayList<>());
    longMetrics.put(Metrics.RECEIVER_LANDMARK_MESSAGES, new ArrayList<>());
    longMetrics.put(Metrics.LANDMARK_SENDER_MESSAGES, new ArrayList<>());
    longMetrics.put(Metrics.MESSAGES, new ArrayList<>());
    longMetrics.put(Metrics.ATTEMPTS, new ArrayList<>());
    longMetrics.put(Metrics.PATH, new ArrayList<>());
    longMetrics.put(Metrics.PATH_SUCCESS, new ArrayList<>());
    longMetrics.put(Metrics.PATH_FAIL, new ArrayList<>());
    longMetrics.put(Metrics.MESSAGES_SUCCESS, new ArrayList<>());
    longMetrics.put(Metrics.MESSAGES_FAIL, new ArrayList<>());
    longMetrics.put(Metrics.SINGLE_PATHS, new ArrayList<>());
    longMetrics.put(Metrics.SINGLE_PATHS_DEST_FOUND, new ArrayList<>());
    longMetrics.put(Metrics.SINGLE_PATHS_DEST_NOT_FOUND, new ArrayList<>());
    longMetrics.put(Metrics.DELAY, new ArrayList<>());
    longMetrics.put(Metrics.DELAY_SUCCESS, new ArrayList<>());
    longMetrics.put(Metrics.DELAY_FAIL, new ArrayList<>());

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

    linkMetricChanges = new ArrayList<>();
    for (int i = 0; i < numEpochs; i++) {
      linkMetricChanges.add(new ConcurrentHashMap<>());
    }
  }

  synchronized void finalizeUpdateWeight(int src, int dst, double weightChange, int epochNumber)
          throws TransactionFailedException {
    LinkStats stats = edgeweights.finalizeUpdateWeight(src, dst, weightChange);
    double previousDev = epochNumber == 0 ? 0 :
            linkMetricChanges.get(epochNumber - 1).getOrDefault(stats.getName(), new LinkStats()).getCurrentDeviation();
    stats.setPreviousDeviation(previousDev);

    double previousBCD = epochNumber == 0 ? 0 :
            linkMetricChanges.get(epochNumber - 1).getOrDefault(stats.getName(), new LinkStats()).getCurrentBCD();
    stats.setPreviousBCD(previousBCD);

    linkMetricChanges.get(epochNumber).put(stats.getName(), stats);
  }

  static double calculateTotalBCD(CreditLinks edgeweights) {
    double totalBCD = 0;
    for (Map.Entry<Edge, LinkWeight> e : edgeweights.getWeights()) {
      totalBCD += e.getValue().getBCD();
    }

    return totalBCD;
  }

  synchronized void incrementPerEpochValue(Metrics name, int currentEpoch) {
    addPerEpochValue(name, 1, currentEpoch);
  }

  private synchronized void addPerEpochDoubleValue(Metrics name, double value, int currentEpoch) {
    perEpochDoubleMetrics.computeIfPresent(name, (k, v) -> {
      v[currentEpoch] += value;
      return v;
    });
  }

  synchronized void addPerEpochValue(Metrics name, int value, int currentEpoch) {
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
    incrementPerEpochValue(Metrics.TRANSACTIONS, currentEpoch);
    if (results.isSuccess()) {
      incrementPerEpochValue(Metrics.SUCCESSES, currentEpoch);
      addPerEpochValue(Metrics.PATH_SUCCESS, results.getSumPathLength(), currentEpoch);
      addPerEpochDoubleValue(Metrics.TOTAL_CREDIT_TRANSACTED_PER_EPOCH, currentTransaction.val, calculateEpoch(currentTransaction));
    }

    this.txStartEndTimes[currentTransaction.index] = new double[]{currentTransaction.startTime,
            currentTransaction.endTime};

    incrementCount(Metrics.PATH, results.getSumPathLength());
    incrementCount(Metrics.RECEIVER_LANDMARK_MESSAGES, results.getSumReceiverLandmarks());
    incrementCount(Metrics.LANDMARK_SENDER_MESSAGES, results.getSumSourceDepths());
    incrementCount(Metrics.MESSAGES, results.getSumMessages());
    incrementCount(Metrics.DELAY, results.getMaxPathLength());
    if (results.isSuccess()) {
      incrementCount(Metrics.ATTEMPTS, currentTransaction.timesRequeued);
      this.success++;
      if (currentTransaction.timesRequeued == 0) {
        this.success_first++;
      }
      incrementCount(Metrics.MESSAGES_ALL, currentTransaction.mes);
      incrementCount(Metrics.PATHS_ALL, currentTransaction.path);
      incrementCount(Metrics.PATH_SUCCESS, results.getSumPathLength());
      incrementCount(Metrics.MESSAGES_SUCCESS, results.getSumMessages());
      incrementCount(Metrics.DELAY_SUCCESS, results.getMaxPathLength());
    } else {
      incrementCount(Metrics.PATH_FAIL, results.getSumPathLength());
      incrementCount(Metrics.MESSAGES_FAIL, results.getSumMessages());
      incrementCount(Metrics.DELAY_FAIL, results.getMaxPathLength());
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

      incrementCount(Metrics.SINGLE_PATHS, pathLength);

      if (index == 0) {
        incrementCount(Metrics.SINGLE_PATHS_DEST_FOUND, pathLength);
        if (!isMaxFlow()) {
          incrementCount(pathSsF.get(j), pathLength);
        }
      } else {
        incrementCount(Metrics.SINGLE_PATHS_DEST_NOT_FOUND, pathLength);
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

  long[] convertListToLongArray(Metrics propName) {
    return convertListToLongArray(longMetrics.get(propName));
  }

  long[] convertListToLongArray(List<Long> list) {
    if (list.isEmpty()) {
      return new long[0];
    }
    return list.stream().mapToLong(l -> l).toArray();
  }

  private synchronized void incrementCount(List<Long> values, int index) {
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

  void incrementCount(Metrics propName, int index) {
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
    for (Metrics dataKey : distributions.keySet()) {
      if (distributions.get(dataKey).getDistribution() != null) {
        succ &= DataWriter.writeWithIndex(distributions.get(dataKey).getDistribution(),
                this.key + dataKey.getFileSuffix(), folder);
      }
    }

    succ &= DataWriter.writeWithoutIndex(this.txStartEndTimes,
            this.key + "_TX_START_END_TIMES", folder);

    for (Metrics m : Metrics.values()) {
      if (m.getSingleName().equals("") &&
              !m.getFileSuffix().equals("") &&
              perEpochDoubleMetrics.get(m) != null) {
        succ &= DataWriter.writeWithIndex(perEpochDoubleMetrics.get(m),
                this.key + m.getFileSuffix(), folder);
      }
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
//  void blockUntilAsyncTransactionsComplete(Queue<Future<TransactionResults>> pendingTransactions) {
//    for (Future<TransactionResults> res : pendingTransactions) {
//      blockUntilAsyncTransactionCompletes(res);
//    }
//  }

  // used for unit tests
  CreditLinks getCreditLinks() {
    return this.edgeweights;
  }

  protected List<int[]> reversePaths(List<int[]> forwardPaths) {
    List<int[]> ret = new LinkedList<>();

    for (int[] fPath : forwardPaths) {
      ret.add(reversePath(fPath));
    }

    return ret;
  }

  protected int[][] reversePaths(int[][] forwardPaths) {
    // init 2d arrary
    int[][] ret = new int[forwardPaths.length][];
    for (int i = 0; i < ret.length; i++) {
      ret[i] = new int[forwardPaths[i].length];
    }

    for (int i = 0; i < forwardPaths.length; i++) {
      ret[i] = reversePath(forwardPaths[i]);
    }

    return ret;
  }

  private int[] reversePath(int[] fPath) {
    int[] rPath = new int[fPath.length];
    for (int i = fPath.length - 1; i >= 0; i--) {
      rPath[rPath.length - i - 1] = fPath[i];
    }
    return rPath;
  }

  private void updatePerEpochMetric(Metrics name, int currentEpoch, double value) {
    perEpochDoubleMetrics.get(name)[currentEpoch] = value;
  }

  void calculateTotalMetrics() {
    perEpochDoubleMetrics.put(Metrics.SUCCESS_RATE_PER_EPOCH,
            new double[getPerEpochMetric(Metrics.TRANSACTIONS).length]);
    perEpochDoubleMetrics.put(Metrics.AVG_SUCCESSFUL_PATH_LENGTH_PER_EPOCH,
            new double[getPerEpochMetric(Metrics.PATH_SUCCESS).length]);
    perEpochDoubleMetrics.put(Metrics.BLOCKED_LINKS_RATIO_PER_EPOCH,
            new double[getPerEpochMetric(Metrics.TRANSACTIONS).length]);

    for (int epochNumber = 0;
         epochNumber < getPerEpochMetric(Metrics.TRANSACTIONS).length;
         epochNumber++) {
      double val = (double) getPerEpochMetric(Metrics.SUCCESSES)[epochNumber] /
              (double) getPerEpochMetric(Metrics.TRANSACTIONS)[epochNumber];
      updatePerEpochMetric(Metrics.SUCCESS_RATE_PER_EPOCH, epochNumber, val);

      val = (double) getPerEpochMetric(Metrics.BLOCKED_LINKS_PER_EPOCH)[epochNumber] /
              (double) getPerEpochMetric(Metrics.TRANSACTIONS)[epochNumber];
      updatePerEpochMetric(Metrics.BLOCKED_LINKS_RATIO_PER_EPOCH, epochNumber, val);

      val = (double) getPerEpochMetric(Metrics.PATH_SUCCESS)[epochNumber] /
              (double) getPerEpochMetric(Metrics.SUCCESSES)[epochNumber];
      updatePerEpochMetric(Metrics.AVG_SUCCESSFUL_PATH_LENGTH_PER_EPOCH, epochNumber, val);

      double runningBCDTotal = epochNumber == 0 ? 0 :
              perEpochDoubleMetrics.get(Metrics.BCD_PER_EPOCH)[epochNumber - 1];
      double runningDeviationTotal = epochNumber == 0 ? 0 :
              perEpochDoubleMetrics.get(Metrics.CREDIT_DEVIATION_PER_EPOCH)[epochNumber - 1];
      for (LinkStats linkStats : linkMetricChanges.get(epochNumber).values()) {
        runningBCDTotal -= linkStats.getPreviousBCD();
        runningBCDTotal += linkStats.getCurrentBCD();

        runningDeviationTotal -= linkStats.getPreviousDeviation();
        runningDeviationTotal += linkStats.getCurrentDeviation();
      }
      updatePerEpochMetric(Metrics.BCD_PER_EPOCH, epochNumber, runningBCDTotal);
      updatePerEpochMetric(Metrics.CREDIT_DEVIATION_PER_EPOCH, epochNumber, runningDeviationTotal);
    }
  }

  int calculateDelayTime(Attack attack) {
    if (attack.getReceiverDelayVariability() == 0) {
      return attack.getReceiverDelayMs();
    }

    double variationPercentage = (attack.getReceiverDelayVariability() + 1) / 100.0;

    // gen random number from 0 to attackDelayVariability, and subtract half
    double randomVariation = ThreadLocalRandom.current().nextDouble(variationPercentage) - variationPercentage / 2;

    // multiply delayTime by that amount
    return (int) ((randomVariation + 1) * attack.getReceiverDelayMs());
  }
}