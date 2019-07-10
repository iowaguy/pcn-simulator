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
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.PriorityBlockingQueue;

import gtna.data.Single;
import gtna.graph.Edge;
import gtna.graph.Edges;
import gtna.graph.Graph;
import gtna.graph.Node;
import gtna.graph.spanningTree.SpanningTree;
import gtna.io.DataWriter;
import gtna.io.graphWriter.GtnaGraphWriter;
import gtna.metrics.Metric;
import gtna.networks.Network;
import gtna.transformation.spanningtree.MultipleSpanningTree;
import gtna.transformation.spanningtree.MultipleSpanningTree.Direct;
import gtna.util.Config;
import gtna.util.Distribution;
import gtna.util.parameter.BooleanParameter;
import gtna.util.parameter.DoubleParameter;
import gtna.util.parameter.IntParameter;
import gtna.util.parameter.Parameter;
import gtna.util.parameter.StringParameter;
import treeembedding.RoutingAlgorithm;
import treeembedding.RunConfig;
import treeembedding.byzantine.Attack;
import treeembedding.byzantine.AttackType;
import treeembedding.byzantine.ByzantineNodeSelection;
import treeembedding.byzantine.NoByzantineNodeSelection;
import treeembedding.credit.exceptions.InsufficientFundsException;
import treeembedding.credit.exceptions.TransactionFailedException;
import treeembedding.credit.partioner.Partitioner;
import treeembedding.treerouting.TreeCoordinates;
import treeembedding.treerouting.Treeroute;
import treeembedding.treerouting.TreerouteSilentW;
import treeembedding.vouteoverlay.Treeembedding;

public class CreditNetwork extends Metric {
  private static final String SINGLE_PATHS_DEST_FOUND = "pathSF"; //distribution of single paths, only discovered paths
  private static final String SINGLE_PATHS_DEST_NOT_FOUND = "pathSNF"; //distribution of single paths, not found dest
  private static final String SINGLE_PATHS = "pathS"; //distribution of single paths
  private static final String ATTEMPTS = "trys"; //Distribution of number of trials needed to get through
  private static final String MESSAGES_ALL = "mesAll"; //distribution of #messages needed, counting re-transactions as part of transaction
  private static final String PATHS_ALL = "pathAll"; //distribution of path length counting re-transactions as one
  private static final String RECEIVER_LANDMARK_MESSAGES = "reLand"; //messages receiver-landmarks communication
  private static final String LANDMARK_SENDER_MESSAGES = "landSen"; //messages sender-landmarks communication
  private static final String MESSAGES = "mes"; //distribution of #messages needed for one transaction trial i.e. each retry counts as a new transaction
  private static final String PATH = "path"; //distribution of path length (sum over all trees!)
  private static final String PATH_SUCCESS = "pathSucc"; //path length successful transactions
  private static final String PATH_FAIL = "pathFail"; //path length failed transactions
  private static final String MESSAGES_SUCCESS = "mesSucc"; //messages successful transactions
  private static final String MESSAGES_FAIL = "mesFail"; //messages failed transactions
  private static final String DELAY = "del"; //distribution of hop delay
  private static final String DELAY_SUCCESS = "delSucc"; //distribution of hop delay, successful queries
  private static final String DELAY_FAIL = "delFail"; //distribution of hop delay, failed queries

  private static final Map<String, String> FILE_SUFFIXES;
  private static final Map<String, String> SINGLE_NAMES;
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
    SINGLE_NAMES.put(MESSAGES, "CREDIT_NETWORK_MES_AV");
    SINGLE_NAMES.put(MESSAGES_ALL, "CREDIT_NETWORK_MES_RE_AV");
    SINGLE_NAMES.put(MESSAGES_SUCCESS, "CREDIT_NETWORK_MES_SUCC_AV");
    SINGLE_NAMES.put(MESSAGES_FAIL, "CREDIT_NETWORK_MES_FAIL_AV");
    SINGLE_NAMES.put(PATH, "CREDIT_NETWORK_PATH_AV");
    SINGLE_NAMES.put(PATHS_ALL, "CREDIT_NETWORK_PATH_RE_AV");
    SINGLE_NAMES.put(PATH_SUCCESS, "CREDIT_NETWORK_PATH_SUCC_AV");
    SINGLE_NAMES.put(PATH_FAIL, "CREDIT_NETWORK_PATH_FAIL_AV");
    SINGLE_NAMES.put(RECEIVER_LANDMARK_MESSAGES, "CREDIT_NETWORK_REC_LAND_MES_AV");
    SINGLE_NAMES.put(LANDMARK_SENDER_MESSAGES, "CREDIT_NETWORK_LAND_SRC_MES_AV");
    SINGLE_NAMES.put(SINGLE_PATHS, "CREDIT_NETWORK_PATH_SINGLE_AV");
    SINGLE_NAMES.put(SINGLE_PATHS_DEST_FOUND, "CREDIT_NETWORK_PATH_SINGLE_FOUND_AV");
    SINGLE_NAMES.put(SINGLE_PATHS_DEST_NOT_FOUND, "CREDIT_NETWORK_PATH_SINGLE_NF_AV");
    SINGLE_NAMES.put(DELAY, "CREDIT_NETWORK_DELAY_AV");
    SINGLE_NAMES.put(DELAY_SUCCESS, "CREDIT_NETWORK_DELAY_SUCC_AV");
    SINGLE_NAMES.put(DELAY_FAIL, "CREDIT_NETWORK_DELAY_FAIL_AV");

  }
  private Map<String, Distribution> distributions;

  //input parameters
  private final double epoch; //interval for stabilization overhead (=epoch between spanning tree recomputations if !dynRepair)
  private Vector<Transaction> transactions; //vector of transactions, sorted by time
  private Treeroute ra; //routing algorithm
  private boolean dynRepair; //true if topology changes are immediately fixed rather than recomputation each epoch
  private boolean multi; //using multi-party computation to determine minimum or do routing adhoc
  private double requeueInt; //interval until a failed transaction is re-tried; irrelevant if !dynRepair as
  //retry is start of next epoch
  private Partitioner part; //method to partition overall transaction value on paths
  private int[] roots; // spanning tree roots
  private int maxTries;
  private Queue<double[]> newLinks;

  private Queue<Edge> zeroEdges;
  private Graph graph;
  private boolean update;

  //computed metrics
  private double[] stab; //stabilization overhead over time (in #messages)
  private double stab_av; //average stab overhead

  private Distribution[] pathsPerTree; //distribution of single paths per tree
  private Distribution[] pathsPerTreeFound; //distribution of single paths per tree, only discovered paths
  private Distribution[] pathsPerTreeNF; //distribution of single paths per tree, not found dest
  private double[] passRoot;
  private double passRootAll = 0;
  private int rootPath = 0;
  private double success_first; //fraction of transactions successful in first try
  private double success; // fraction of transactions successful at all
  private double[] succs;

  private ByzantineNodeSelection byzSelection;
  private Set<Integer> byzantineNodes;
  private Attack attack;
  private boolean areTransactionsConcurrent;
  private Logger log;
  private ExecutorService executor;
  private Queue<Transaction> toRetry;

  private Map<String, List<Long>> longMetrics;

  private double cur_succ = 0;
  private int cur_count = 0;
  private int transactionCount = 0;
  private List<Integer> cAllPath = new ArrayList<>();


  private List<List<Long>> pathSs;
  private List<List<Long>> pathSsF;
  private List<List<Long>> pathSsNF;
  private List<List<Integer>> cPerPath;

  private int networkLatency;
  private CreditLinks creditLinks;

  // used for unit tests
  CreditLinks getCreditLinks() {
    return this.creditLinks;
  }

  public CreditNetwork(String file, String name, double epoch, RoutingAlgorithm algo,
                       double requeueInt, Partitioner part, int[] roots, int max,
                       String links, boolean up, ByzantineNodeSelection byzSelection, Attack attack,
                       RunConfig runConfig) {
    super("CREDIT_NETWORK", new Parameter[]{new StringParameter("NAME", name), new DoubleParameter("EPOCH", epoch),
            new StringParameter("RA", algo.getTreeroute().getKey()), new BooleanParameter("DYN_REPAIR", algo.doesDynamicRepair()),
            new BooleanParameter("MULTI", algo.usesMPC()), new IntParameter("TREES", roots.length),
            new DoubleParameter("REQUEUE_INTERVAL", requeueInt), new StringParameter("PARTITIONER", part.getName()),
            new IntParameter("MAX_TRIES", max)});
    this.epoch = epoch;
    this.ra = algo.getTreeroute();
    this.multi = algo.usesMPC();
    this.dynRepair = algo.doesDynamicRepair();
    transactions = this.readList(file);
    this.requeueInt = requeueInt;
    this.part = part;
    this.roots = roots;
    this.maxTries = max;
    this.areTransactionsConcurrent = runConfig.areTransactionsConcurrent();
    this.networkLatency = runConfig.getNetworkLatencyMs();
    if (links != null) {
      this.newLinks = this.readLinks(links);
    } else {
      this.newLinks = new LinkedList<>();
    }
    this.update = up;

    if (byzSelection == null) {
      this.byzSelection = new NoByzantineNodeSelection();
    } else {
      this.byzSelection = byzSelection;
    }

    this.attack = attack;
    this.zeroEdges = new ConcurrentLinkedQueue<>();

    LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
    Configuration config = ctx.getConfiguration();
    LoggerConfig loggerConfig = config.getLoggerConfig(LogManager.ROOT_LOGGER_NAME);
    loggerConfig.setLevel(Level.getLevel(runConfig.getLogLevel().toUpperCase()));
    ctx.updateLoggers();  // This causes all Loggers to refetch information from their LoggerConfig.
    this.log = LogManager.getLogger();

    toRetry = new PriorityBlockingQueue<>();

    int threads = 1;
    if (this.areTransactionsConcurrent) {
      threads = runConfig.getConcurrentTransactionsCount();
    }
    executor = Executors.newFixedThreadPool(threads);

    pathSs = new ArrayList<>(this.roots.length);
    pathSsF = new ArrayList<>(this.roots.length);
    pathSsNF = new ArrayList<>(this.roots.length);
    cPerPath = new ArrayList<>(this.roots.length);

    for (int i = 0; i < this.roots.length; i++) {
      pathSs.add(new ArrayList<>());
      pathSsF.add(new ArrayList<>());
      pathSsNF.add(new ArrayList<>());
      cPerPath.add(new ArrayList<>());
      cPerPath.get(i).add(0);
      cPerPath.get(i).add(1);
    }

    longMetrics = new ConcurrentHashMap<>();
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

    // initialize to start with zeros
    cAllPath.add(0);
    cAllPath.add(0);

    this.distributions = new HashMap<>();
  }

  public CreditNetwork(String file, String name, double epoch, RoutingAlgorithm algo,
                       double requeueInt, Partitioner part, int[] roots, int max,
                       String links, ByzantineNodeSelection byz, Attack attack,
                       RunConfig runConfig) {
    this(file, name, epoch, algo, requeueInt, part, roots, max, links, true, byz,
            attack, runConfig);
  }

  public CreditNetwork(String file, String name, double epoch, RoutingAlgorithm algo,
                       double requeueInt, Partitioner part, int[] roots, int max,
                       RunConfig runConfig) {
    this(file, name, epoch, algo, requeueInt, part, roots, max, null, true,
            null, null, runConfig);
  }

  public CreditNetwork(String file, String name, double epoch, RoutingAlgorithm algo,
                       double requeueInt, Partitioner part, int[] roots, int max,
                       boolean up, ByzantineNodeSelection byzSelection, Attack attack, RunConfig runConfig) {
    this(file, name, epoch, algo, requeueInt, part, roots, max, null, up, byzSelection,
            attack, runConfig);
  }

  private Future<TransactionResults> transactionResultsFuture(Transaction cur, Graph g, Node[] nodes,
                                                              boolean[] exclude, CreditLinks edgeweights) {
    return executor.submit(() -> transact(cur, g, nodes, exclude, edgeweights));
  }

  private TransactionResults transact(Transaction currentTransaction, Graph g, Node[] nodes, boolean[] exclude,
                                      CreditLinks edgeweights) {
    TransactionResults results = null;
    try {
      // execute the transaction
      if (this.multi) {
        results = this.routeMulti(currentTransaction, g, nodes, exclude, edgeweights);
      } else {
        results = this.routeAdhoc(currentTransaction, g, nodes, exclude, edgeweights);
      }
    } catch (TransactionFailedException e) {
      e.printStackTrace();
    }

    currentTransaction.addPath(results.getSumPathLength());
    currentTransaction.addMes(results.getRes4());

    //re-queue if necessary
    if (!results.isSuccess()) {
      Random rand = new Random();
      currentTransaction.incRequeue(currentTransaction.time + rand.nextDouble() * this.requeueInt);
      if (currentTransaction.requeue <= this.maxTries) {
        toRetry.add(currentTransaction);
      } else {
        incrementCount("mesAll", currentTransaction.mes);
        incrementCount("pathAll", currentTransaction.path);
      }
    }

    if (!this.update) {
      this.weightUpdate(edgeweights, results.getModifiedEdges());
    }

    calculateMetrics(results, currentTransaction);
    return results;
  }

  private synchronized void calculateMetrics(TransactionResults results,
                                             Transaction currentTransaction) {
    cur_count++;
    if (results.isSuccess()) {
      cur_succ++;
    }

    //3 update metrics accordingly
    incrementCount("path", results.getSumPathLength());
    incrementCount("reLand", results.getSumReceiverLandmarks());
    incrementCount("landSen", results.getSumSourceDepths());
    incrementCount("mes", results.getRes4());
    incrementCount("del", results.getMaxPathLength());
    if (results.isSuccess()) {
      incrementCount(ATTEMPTS, currentTransaction.requeue);
      this.success++;
      if (currentTransaction.requeue == 0) {
        this.success_first++;
      }
      incrementCount("mesAll", currentTransaction.mes);
      incrementCount("pathAll", currentTransaction.path);
      incrementCount("pathSucc", results.getSumPathLength());
      incrementCount("mesSucc", results.getRes4());
      incrementCount("delSucc", results.getMaxPathLength());
    } else {
      incrementCount("pathFail", results.getSumPathLength());
      incrementCount("mesFail", results.getRes4());
      incrementCount("delFail", results.getMaxPathLength());
    }
    for (int j = 0; j < results.getPathLengths().length; j++) {
      int index = 0;
      if (results.getPathLengths()[j] < 0) {
        index = 1;
      }
      incrementIntegerCount(cPerPath.get(j), index);
      //.set(index, cPerPath.get(j).get(index) + 1);
      incrementIntegerCount(cAllPath, index);
      //cAllPath.set(index, cAllPath.get(index) + 1);
      int pathLength = Math.abs(results.getPathLengths()[j]);
      incrementCount(SINGLE_PATHS, pathLength);
      incrementCount(pathSs.get(j), pathLength);
      if (index == 0) {
        incrementCount(SINGLE_PATHS_DEST_FOUND, pathLength);
        incrementCount(pathSsF.get(j), pathLength);
      } else {
        incrementCount(SINGLE_PATHS_DEST_NOT_FOUND, pathLength);
        incrementCount(pathSsNF.get(j), pathLength);
      }
    }
  }

  private Transaction getNextTransaction() {
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

  private boolean areTransactionsAvailable() {
    return transactionCount < this.transactions.size() || !toRetry.isEmpty();
  }

  private int addLinks(Transaction currentTransaction, Graph g) {
    int stabilityMessages = 0;
    if (!this.newLinks.isEmpty()) {
      double timeAdded = this.newLinks.peek()[0];
      while (timeAdded <= currentTransaction.time) {
        double[] link = this.newLinks.poll();
        int st = this.addLink((int) link[1], (int) link[2], link[3], g);
        stabilityMessages += st;
        timeAdded = this.newLinks.isEmpty() ? Double.MAX_VALUE : this.newLinks.peek()[0];
      }
    }

    return stabilityMessages;
  }

  // this will block until the current result is available
  private void blockUntilAsyncTransactionsComplete(Queue<Future<TransactionResults>> pendingTransactions) {
    for (Future<TransactionResults> res : pendingTransactions) {
      try {
        res.get();
      } catch (InterruptedException | ExecutionException e) {
        log.error(e.getMessage());
        e.printStackTrace();
      }
    }
  }

  private int calculateEpoch(Transaction t) {
    return (int) Math.floor(t.time / epoch);
  }

  @Override
  public void computeData(Graph g, Network n, HashMap<String, Metric> m) {
    //init: construct trees (currently randomly) and init variables

    Treeembedding embed = new Treeembedding("T", 60, roots, MultipleSpanningTree.Direct.TWOPHASE);
    if (!g.hasProperty("SPANNINGTREE_0")) {
      g = embed.transform(g);
    }

    int totalTransactionAttemptCount = 0;

    success_first = 0;
    success = 0;
    this.passRoot = new double[this.roots.length];
    Vector<Integer> stabMes = new Vector<>();
    Vector<Double> succR = new Vector<>();
    Node[] nodes = g.getNodes();
    boolean[] exclude = new boolean[nodes.length];

    // generate byzantine nodes
    this.byzantineNodes = this.byzSelection.conscript(nodes);

    int lastEpoch = 0;
    int stabilizationMessages = 0;
    Queue<Future<TransactionResults>> pendingTransactions = new LinkedList<>();

    CreditLinks edgeweights = (CreditLinks) g.getProperty("CREDIT_LINKS");
    while (areTransactionsAvailable()) {
      Transaction currentTransaction = getNextTransaction();

      // calculate epoch
      int currentEpoch = calculateEpoch(currentTransaction);

      // add new links if any
      stabilizationMessages += addLinks(currentTransaction, g);

      totalTransactionAttemptCount++;
      if (log.isInfoEnabled()) {
        log.info(currentTransaction.toString());
      }

      //1: check if and how many spanning tree re-construction took place since last transaction
      //do 1 (!) re-computation if there was any & set stabilization cost
      if (currentEpoch != lastEpoch) {
        if (!this.dynRepair) {
          // if the epoch has changed and there are no dynamic repairs, wait until async
          // transactions are done, then rebalance
          blockUntilAsyncTransactionsComplete(pendingTransactions);

          log.debug("Recompute spanning tree");
          for (int i = 0; i < roots.length; i++) {
            g.removeProperty("SPANNINGTREE_" + i);
            g.removeProperty("TREE_COORDINATES_" + i);
          }
          g = embed.transform(g);
          for (int j = lastEpoch + 1; j <= currentEpoch; j++) {
            stabMes.add(this.roots.length * 2 * this.computeNonZeroEdges(g, edgeweights));
          }
        } else {
          stabMes.add(stabilizationMessages);
          for (int j = lastEpoch + 2; j <= currentEpoch; j++) {
            stabMes.add(0);
          }
          stabilizationMessages = 0;
        }
        cur_succ = cur_count == 0 ? 1 : cur_succ / cur_count;
        succR.add(cur_succ);
        for (int j = lastEpoch + 2; j <= currentEpoch; j++) {
          succR.add(1.0);
        }
        cur_count = 0;
        cur_succ = 0;
      }

      // collect result futures
      Future<TransactionResults> futureResults = transactionResultsFuture(currentTransaction, g, nodes, exclude, edgeweights);
      pendingTransactions.add(futureResults);

      //4 post-processing: remove edges set to 0, update spanning tree if dynRapir
      lastEpoch = currentEpoch;
      if (this.dynRepair && zeroEdges != null) {
        for (int j = 0; j < this.roots.length; j++) {
          SpanningTree sp = (SpanningTree) g.getProperty("SPANNINGTREE_" + j);
          while (!this.zeroEdges.isEmpty()) {
            Edge e = this.zeroEdges.remove();
            int s = e.getSrc();
            int t = e.getDst();
            int cut = -1;
            if (sp.getParent(s) == t) {
              cut = s;
            }
            if (sp.getParent(t) == s) {
              cut = t;
            }
            if (cut != -1) {
              if (log.isDebugEnabled()) {
                log.debug("Repair tree " + j + " at expired edge (" + s + "," + t + ")");
              }
              TreeCoordinates coords = (TreeCoordinates) g.getProperty("TREE_COORDINATES_" + j);
              stabilizationMessages = stabilizationMessages + this.repairTree(nodes, sp, coords,
                      cut, (CreditLinks) g.getProperty("CREDIT_LINKS"));
            }
          }
        }
      }
    }
    this.executor.shutdown();
    // don't want metrics to be computed before all transactions are done
    blockUntilAsyncTransactionsComplete(pendingTransactions);
    this.creditLinks = edgeweights;

    if (this.dynRepair) {
      stabMes.add(stabilizationMessages);
    }

    //compute metrics


    distributions.put(PATH, new Distribution(convertListToLongArray(PATH), totalTransactionAttemptCount));
    distributions.put(MESSAGES, new Distribution(convertListToLongArray(MESSAGES), totalTransactionAttemptCount));
    distributions.put(PATHS_ALL, new Distribution(convertListToLongArray(PATHS_ALL), transactions.size()));
    distributions.put(MESSAGES_ALL, new Distribution(convertListToLongArray(MESSAGES_ALL), transactions.size()));
    distributions.put(PATH_SUCCESS, new Distribution(convertListToLongArray(PATH_SUCCESS), (int) this.success));
    distributions.put(MESSAGES_SUCCESS, new Distribution(convertListToLongArray(MESSAGES_SUCCESS), (int) this.success));
    distributions.put(PATH_FAIL, new Distribution(convertListToLongArray(PATH_FAIL), totalTransactionAttemptCount - (int) this.success));
    distributions.put(MESSAGES_FAIL,new Distribution(convertListToLongArray(MESSAGES_FAIL), totalTransactionAttemptCount - (int) this.success));
    distributions.put(RECEIVER_LANDMARK_MESSAGES, new Distribution(convertListToLongArray(RECEIVER_LANDMARK_MESSAGES), totalTransactionAttemptCount));
    distributions.put(LANDMARK_SENDER_MESSAGES, new Distribution(convertListToLongArray(LANDMARK_SENDER_MESSAGES), totalTransactionAttemptCount));
    distributions.put(ATTEMPTS, new Distribution(convertListToLongArray(ATTEMPTS), (int) this.success));
    distributions.put(SINGLE_PATHS, new Distribution(convertListToLongArray(SINGLE_PATHS), cAllPath.get(0) + cAllPath.get(1)));
    distributions.put(SINGLE_PATHS_DEST_FOUND, new Distribution(convertListToLongArray(SINGLE_PATHS_DEST_FOUND), cAllPath.get(0)));
    distributions.put(SINGLE_PATHS_DEST_NOT_FOUND, new Distribution(convertListToLongArray(SINGLE_PATHS_DEST_NOT_FOUND), cAllPath.get(1)));
    distributions.put(DELAY, new Distribution(convertListToLongArray(DELAY), totalTransactionAttemptCount));
    distributions.put(DELAY_SUCCESS, new Distribution(convertListToLongArray(DELAY_SUCCESS), (int) this.success));
    distributions.put(DELAY_FAIL, new Distribution(convertListToLongArray(DELAY_FAIL), totalTransactionAttemptCount - (int) this.success));

    this.pathsPerTree = new Distribution[this.roots.length];
    this.pathsPerTreeFound = new Distribution[this.roots.length];
    this.pathsPerTreeNF = new Distribution[this.roots.length];
    for (int j = 0; j < this.pathsPerTree.length; j++) {
      this.pathsPerTree[j] = new Distribution(convertListToLongArray(pathSs.get(j)), cPerPath.get(j).get(0) + cPerPath.get(j).get(1));
      this.pathsPerTreeFound[j] = new Distribution(convertListToLongArray(pathSsF.get(j)), cPerPath.get(j).get(0));
      this.pathsPerTreeNF[j] = new Distribution(convertListToLongArray(pathSsNF.get(j)), cPerPath.get(j).get(1));
    }
    this.success = this.success / (double) transactions.size();
    this.success_first = this.success_first / (double) transactions.size();
    stab = new double[stabMes.size()];
    this.stab_av = 0;
    for (int i = 0; i < this.stab.length; i++) {
      stab[i] = stabMes.get(i);
      this.stab_av = this.stab_av + stab[i];
    }
    this.succs = new double[succR.size()];
    for (int i = 0; i < this.succs.length; i++) {
      succs[i] = succR.get(i);
    }
    this.stab_av = this.stab_av / (double) stab.length;
    this.passRootAll = this.passRootAll / this.rootPath;
    for (int j = 0; j < this.passRoot.length; j++) {
      this.passRoot[j] = this.passRoot[j] / totalTransactionAttemptCount;
    }
    this.graph = g;

  }

  private long[] convertListToLongArray(String propName) {
    return convertListToLongArray(longMetrics.get(propName));
  }

  private long[] convertListToLongArray(List<Long> list) {
    if (list.isEmpty()) {
      return new long[0];
    }
    return list.stream().mapToLong(l -> l).toArray();
  }

  private int computeNonZeroEdges(Graph g, CreditLinks ew) {
    Edges edges = g.getEdges();
    int c = 0;
    for (Edge e : edges.getEdges()) {
      if (e.getSrc() < e.getDst()) {
        if (ew.getMaxTransactionAmount(e.getSrc(), e.getDst(), areTransactionsConcurrent) > 0 ||
                ew.getMaxTransactionAmount(e.getDst(), e.getSrc(), areTransactionsConcurrent) > 0) {
          c++;
        }
      }
    }
    return c;
  }

  /**
   * reconnect disconnected branch with root subroot
   */
  private synchronized int repairTree(Node[] nodes, SpanningTree sp, TreeCoordinates coords, int subroot, CreditLinks ew) {
    if (!update) {
      int mes = 0;
      Queue<Integer> q1 = new LinkedList<Integer>();
      Queue<Integer> q2 = new LinkedList<Integer>();
      q1.add(subroot);
      while (!q1.isEmpty()) {
        int node = q1.poll();
        int[] kids = sp.getChildren(node);
        for (int kid : kids) {
          mes++;
          q1.add(kid);
        }
        mes = mes + MultipleSpanningTree.potParents(graph, nodes[node],
                Direct.EITHER, ew).length;
      }
      return mes;
    }
    //remove old tree info of all descendants of subroot
    int mes = 0;
    Queue<Integer> q1 = new LinkedList<Integer>();
    Queue<Integer> q2 = new LinkedList<Integer>();
    q1.add(subroot);
    while (!q1.isEmpty()) {
      int node = q1.poll();
      int[] kids = sp.getChildren(node);
      for (int i = 0; i < kids.length; i++) {
        mes++;
        q1.add(kids[i]);
      }
      sp.removeNode(node);
      q2.add(node);
    }


    Random rand = new Random();
    MultipleSpanningTree.Direct[] dir = {Direct.BOTH, Direct.EITHER, Direct.NONE};
    for (int k = 0; k < dir.length; k++) {
      int count = q2.size();
      while (count > 0) {
        int node = q2.poll();
        Vector<Integer> bestN = new Vector<Integer>();
        int mind = Integer.MAX_VALUE;
        int[] out = MultipleSpanningTree.potParents(graph, nodes[node],
                dir[k], ew);
        for (int i : out) {
          if (sp.getParent(i) != -2) {
            if (sp.getDepth(i) < mind) {
              mind = sp.getDepth(i);
              bestN = new Vector<Integer>();
            }
            if (sp.getDepth(i) == mind) {
              bestN.add(i);
            }
          }
        }
        if (bestN.size() > 0) {
          mes = mes + MultipleSpanningTree.potParents(graph, nodes[node],
                  Direct.EITHER, ew).length;
          int pa = bestN.get(rand.nextInt(bestN.size()));
          sp.addParentChild(pa, node);
          long[] pa_coord = coords.getCoord(pa);
          long[] child_coord = new long[pa_coord.length + 1];
          for (int i = 0; i < pa_coord.length; i++) {
            child_coord[i] = pa_coord[i];
          }
          child_coord[pa_coord.length] = rand.nextInt();
          coords.setCoord(node, child_coord);
          count = q2.size();
        } else {
          q2.add(node);
          count--;
        }
      }
    }
    return mes;
  }

  private int addLink(int src, int dst, double weight, Graph g) {
    CreditLinks edgeweights = (CreditLinks) g.getProperty("CREDIT_LINKS");
    if (log.isDebugEnabled()) {
      log.debug("Added link " + src + " " + dst + " " + weight);
    }
    LinkWeight ws;
    double old;
    if (src < dst) {
      ws = edgeweights.getWeights(src, dst);
      old = ws.getMax();
      ws.setMax(weight);
    } else {
      ws = edgeweights.getWeights(dst, src);
      old = ws.getMin();
      ws.setMin(-weight);
    }

    int st = 0;
    if (this.dynRepair) {
      Node[] nodes = g.getNodes();
      if (old == 0 && weight != 0) {
        //new edge might be useful if any of nodes connected to tree by zero edge
        for (int j = 0; j < this.roots.length; j++) {
          SpanningTree sp = (SpanningTree) g.getProperty("SPANNINGTREE_" + j);
          boolean zpSrc = isZeroPath(sp, src, edgeweights);
          boolean zpDst = isZeroPath(sp, dst, edgeweights);
          if (zpSrc) {
            TreeCoordinates coords = (TreeCoordinates) g.getProperty("TREE_COORDINATES_" + j);
            st = st + repairTree(nodes, sp, coords, src, (CreditLinks) g.getProperty("CREDIT_LINKS"));
          }
          if (zpDst) {
            TreeCoordinates coords = (TreeCoordinates) g.getProperty("TREE_COORDINATES_" + j);
            st = st + repairTree(nodes, sp, coords, dst, (CreditLinks) g.getProperty("CREDIT_LINKS"));
          }
        }
      }
      if (old != 0 && weight == 0) {
        //expired edge => reconnect
        for (int j = 0; j < this.roots.length; j++) {
          SpanningTree sp = (SpanningTree) g.getProperty("SPANNINGTREE_" + j);
          int cut = -1;
          if (sp.getParent(src) == dst) {
            cut = src;
          }
          if (sp.getParent(dst) == src) {
            cut = dst;
          }
          if (cut != -1) {
            if (log.isDebugEnabled()) {
              log.debug("Repair tree " + j + " at expired edge (" + src + "," + dst + ")");
            }
            TreeCoordinates coords = (TreeCoordinates) g.getProperty("TREE_COORDINATES_" + j);
            st = st + repairTree(nodes, sp, coords, cut, (CreditLinks) g.getProperty("CREDIT_LINKS"));
          }

        }
      }
    }
    return st;
  }

  private boolean isZeroPath(SpanningTree sp, int node, CreditLinks edgeweights) {
    int parent = sp.getParent(node);
    while (parent != -1) {
      if (edgeweights.getMaxTransactionAmount(node, parent, areTransactionsConcurrent) > 0 &&
              edgeweights.getMaxTransactionAmount(parent, node, areTransactionsConcurrent) > 0) {
        node = parent;
        parent = sp.getParent(node);
      } else {
        return true;
      }
    }
    return false;
  }

  // reset to old weights if failed
  private void transactionFailed(CreditLinks edgeweights, Map<Edge, LinkWeight> modifiedEdges) {
    this.weightUpdate(edgeweights, modifiedEdges);
    this.zeroEdges = new ConcurrentLinkedQueue<>();
  }

  private void simulateNetworkLatency() {
    try {
      Thread.sleep(this.networkLatency);
    } catch (InterruptedException e) {
      // do nothing
    }
  }
  /**
   * Step through transaction one hop at a time, and returns its success status
   *
   * @return true for a successful transaction, false otherwise
   */
  private boolean stepThroughTransaction(double[] vals, int[][] paths, CreditLinks edgeweights, Map<Edge, LinkWeight> modifiedEdges) {
    if (vals == null) {
      transactionFailed(edgeweights, modifiedEdges);
      return false;
    }
    for (int treeIndex = 0; treeIndex < paths.length; treeIndex++) {
      if (vals[treeIndex] != 0) {
        if (paths[treeIndex][paths[treeIndex].length - 1] == -1) {
          // could not find a path
          transactionFailed(edgeweights, modifiedEdges);
          return false;
        }
        int currentNodeId = paths[treeIndex][0];
        for (int nodeIndex = 1; nodeIndex < paths[treeIndex].length; nodeIndex++) {
          simulateNetworkLatency();

          // Attack logic
          if (attack != null && attack.getType() == AttackType.DROP_ALL) {
            if (this.byzantineNodes.contains(paths[treeIndex][nodeIndex])) {
              // do byzantine action
              transactionFailed(edgeweights, modifiedEdges);
              return false;
            }
          }

          int nextNodeId = paths[treeIndex][nodeIndex];
          Edge edge = CreditLinks.makeEdge(currentNodeId, nextNodeId);
          LinkWeight weights = edgeweights.getWeights(edge);
          if (!modifiedEdges.containsKey(edge)) {
            modifiedEdges.put(edge, weights);
          }

          try {
            if (log.isInfoEnabled()) {
              log.info("Prepare: cur=" + currentNodeId + "; next=" + nextNodeId + "; val=" + vals[treeIndex]);
            }
            edgeweights.prepareUpdateWeight(currentNodeId, nextNodeId, vals[treeIndex],
                    areTransactionsConcurrent);
          } catch (InsufficientFundsException e) {
            transactionFailed(edgeweights, modifiedEdges);
            return false;
          }

          currentNodeId = nextNodeId;
        }
      }
    }
    // update weights
    this.setZeros(edgeweights, modifiedEdges);
    return true;
  }

  private void finalizeTransaction(double[] vals, int[][] paths, CreditLinks edgeweights,
                                   Map<Edge, LinkWeight> modifiedEdges)
          throws TransactionFailedException {
    if (vals == null) {
      throw new TransactionFailedException("Transaction values cannot be null");
    }

    for (int treeIndex = 0; treeIndex < paths.length; treeIndex++) {
      if (vals[treeIndex] != 0) {
        int currentNodeIndex = paths[treeIndex][0];
        for (int nodeIndex = 1; nodeIndex < paths[treeIndex].length; nodeIndex++) {
          simulateNetworkLatency();
          int nextNodeIndex = paths[treeIndex][nodeIndex];
          Edge edge = CreditLinks.makeEdge(currentNodeIndex, nextNodeIndex);
          if (!modifiedEdges.containsKey(edge)) {
            log.debug("Removing updated edge from set");
            modifiedEdges.remove(edge);
          }
          if (log.isInfoEnabled()) {
            log.info("Finalize: cur=" + currentNodeIndex + "; next=" + nextNodeIndex + "; val=" + vals[treeIndex]);
          }
          edgeweights.finalizeUpdateWeight(currentNodeIndex, nextNodeIndex, vals[treeIndex],
                  areTransactionsConcurrent);

          if (edgeweights.getMaxTransactionAmount(currentNodeIndex, nextNodeIndex) == 0) {
            this.zeroEdges.add(edge);
          }
          currentNodeIndex = nextNodeIndex;

        }
      }
    }
  }

  /**
   * routing using a multi-part computation: costs are i) finding path (one-way, but 3x path length
   * as each neighbor needs to sign its predecessor and successors) ii) sending shares to all
   * landmarks/roots from receiver iii) sending results to sender from all landmarks/roots iv)
   * testing paths (two-way) v) updating credit (one-way)
   *
   * @return {success?0:-1, sum(pathlength), receiver-landmark, landmarks-sender,overall message
   *         count, delay, p1, p2,...}
   */
  private TransactionResults routeMulti(Transaction cur, Graph g, Node[] nodes, boolean[] exclude,
                                        CreditLinks edgeweights) throws TransactionFailedException {
    int[][] paths = new int[roots.length][];
    double[] vals;
    int src = cur.src;
    int dest = cur.dst;
    //compute paths and minimum credit along the paths
    double[] mins = new double[roots.length];
    for (int treeIndex = 0; treeIndex < mins.length; treeIndex++) {
      paths[treeIndex] = ra.getRoute(src, dest, treeIndex, g, nodes, exclude);

      if (paths[treeIndex][paths[treeIndex].length - 1] == dest) {
        int currentNodeIndex = src;
        double currentMaxForPath = Double.MAX_VALUE;
        for (int nodeIndex = 1; nodeIndex < paths[treeIndex].length; nodeIndex++) {
          int nextNodeIndex = paths[treeIndex][nodeIndex];
          double maxTransactionAmount = edgeweights.getMaxTransactionAmount(currentNodeIndex,
                  nextNodeIndex, areTransactionsConcurrent);
          if (maxTransactionAmount < currentMaxForPath) {
            currentMaxForPath = maxTransactionAmount;
          }
          currentNodeIndex = nextNodeIndex;
        }
        mins[treeIndex] = currentMaxForPath;
      }

    }
    //partition transaction value
    vals = part.partition(g, src, dest, cur.val, mins);

    //check if transaction works
    Map<Edge, LinkWeight> modifiedEdges = new HashMap<>();
    boolean successful = stepThroughTransaction(vals, paths, edgeweights, modifiedEdges);
    if (successful) {
      // TODO simulate attack
      finalizeTransaction(vals, paths, edgeweights, modifiedEdges);
    }

    //compute metrics
    TransactionResults res = new TransactionResults(this.roots.length);

    //success
    res.setSuccess(successful);

    res.setModifiedEdges(modifiedEdges);

    //path length
    for (int j = 0; j < paths.length; j++) {
      if (paths[j][paths[j].length - 1] == dest) {
        res.addSumPathLength(paths[j].length - 1);
        res.addPathLength(j, paths[j].length - 1);
      } else {
        int pathLength = paths[j].length - 2;
        res.addSumPathLength(pathLength);
        res.addPathLength(j, -pathLength);
      }
    }
    //receiver-landmarks
    for (int j = 0; j < paths.length; j++) {
      SpanningTree sp = (SpanningTree) g.getProperty("SPANNINGTREE_" + j);
      int d = sp.getDepth(dest);
      if (paths[j][paths[j].length - 1] == dest) {
        res.addSumReceiverLandmarks(d * paths.length);
      }
    }
    //landmarks-sender
    for (int j = 0; j < paths.length; j++) {
      SpanningTree sp = (SpanningTree) g.getProperty("SPANNINGTREE_" + j);
      int d = sp.getDepth(src);
      if (paths[j][paths[j].length - 1] == dest) {
        res.addSumSourceDepths(d);
      }
    }

    //all
    res.setRes4(res.getSumPathLength() + res.getSumReceiverLandmarks() + res.getSumSourceDepths());
    for (int j = 0; j < paths.length; j++) {
      if (vals != null && vals[j] > 0) {
        res.setRes4(res.getRes4() + 2 * (paths[j].length - 1));
      }
    }
    //max path
    int max = 0;
    int maxp = 0;
    for (int j = 0; j < paths.length; j++) {
      int pl;
      int last;
      if (ra instanceof TreerouteSilentW) {
        SpanningTree sp = (SpanningTree) g.getProperty("SPANNINGTREE_" + j);
        pl = Math.max(sp.getDepth(src), sp.getDepth(dest));
        last = sp.getSrc();
      } else {
        pl = paths[j].length - 1;
        last = dest;
      }
      for (int i = 0; i < paths.length; i++) {
        SpanningTree sp = (SpanningTree) g.getProperty("SPANNINGTREE_" + i);
        if (pl + sp.getDepth(last) > max) {
          max = pl + sp.getDepth(last);
        }
      }
      if (vals != null && vals[j] > 0 && paths[j] != null) {
        if (paths[j].length - 1 > maxp) {
          maxp = paths[j].length - 1;
        }
      }
    }
    int d = 0;
    for (int i = 0; i < paths.length; i++) {
      SpanningTree sp = (SpanningTree) g.getProperty("SPANNINGTREE_" + i);
      if (sp.getDepth(src) > d) {
        d = sp.getDepth(src);
      }
    }
    max = max + d;
    if (vals != null) {
      max = max + 2 * maxp;
    }

    res.setMaxPathLength(max);
    this.setRoots(paths);
    return res;
  }

  private TransactionResults routeAdhoc(Transaction cur, Graph g, Node[] nodes, boolean[] exclude,
                                        CreditLinks edgeweights) throws TransactionFailedException {
    int[][] paths = new int[roots.length][];
    int src = cur.src;
    int dest = cur.dst;
    //distribute values on paths
    double[] vals = this.part.partition(g, src, dest, cur.val, roots.length);

    // build paths
    for (int treeIndex = 0; treeIndex < paths.length; treeIndex++) {
      if (vals[treeIndex] != 0) {
        int s = src;
        int d = dest;
        if (vals[treeIndex] < 0) {
          s = dest;
          d = src;
        }
        paths[treeIndex] = this.ra.getRoute(s, d, treeIndex, g, nodes, exclude, edgeweights, vals[treeIndex]);
      }
    }
    //check if transaction works
    Map<Edge, LinkWeight> modifiedEdges = new HashMap<>();
    boolean successful = stepThroughTransaction(vals, paths, edgeweights, modifiedEdges);
    if (successful) {
      // TODO simulate attack
      finalizeTransaction(vals, paths, edgeweights, modifiedEdges);
    }

    //compute metrics
    TransactionResults res = new TransactionResults(this.roots.length);

    //success
    res.setSuccess(successful);

    res.setModifiedEdges(modifiedEdges);

    //path length
    for (int j = 0; j < paths.length; j++) {
      if (paths[j][paths[j].length - 1] == dest) {
        res.addSumPathLength(paths[j].length - 1);
        res.addPathLength(j, paths[j].length - 1);
      } else {
        res.addSumPathLength(paths[j].length - 2);
        res.addPathLength(j, -(paths[j].length - 2));
      }
    }
    //overall messages
    if (res.isSuccess()) {
      res.setRes4(3 * res.getSumPathLength());
    } else {
      res.setRes4(2 * res.getSumReceiverLandmarks());
    }
    //max path length
    int max = 0;
    for (int j = 0; j < paths.length; j++) {
      if (vals[j] > 0) {
        if (paths[j][paths[j].length - 1] == dest) {
          if (paths[j].length > max) {
            max = paths[j].length;
          }
        } else {
          if (paths[j].length - 1 > max) {
            max = paths[j].length - 1;
          }
        }
      }
    }

    res.setMaxPathLength(2 * max);
    this.setRoots(paths);
    return res;
  }

  private void weightUpdate(CreditLinks edgeweights, Map<Edge, LinkWeight> updatedEdges) {
    for (Entry<Edge, LinkWeight> entry : updatedEdges.entrySet()) {
      edgeweights.setWeight(entry.getKey(), entry.getValue());
    }
  }

  /**
   * This function checks if any values in `updatedEdges` have a new weight of zero. If so, it adds
   * them to the collection of zero edges.
   *
   * @param edgeweights  the current edge weights
   * @param updatedEdges the edges that were affected in the transaction
   */
  private void setZeros(CreditLinks edgeweights, Map<Edge, LinkWeight> updatedEdges) {
    for (Entry<Edge, LinkWeight> entry : updatedEdges.entrySet()) {
      int src = entry.getKey().getSrc();
      int dst = entry.getKey().getDst();
      if (edgeweights.getMaxTransactionAmount(src, dst, areTransactionsConcurrent) == 0) {
        this.zeroEdges.add(CreditLinks.makeEdge(src, dst));
      }
    }
  }

  @Override
  public boolean writeData(String folder) {
    boolean succ = true;
    for (String dataKey: distributions.keySet()) {
      if (distributions.get(dataKey).getDistribution() != null) {
        succ &= DataWriter.writeWithIndex(distributions.get(dataKey).getDistribution(),
                this.key + FILE_SUFFIXES.get(dataKey), folder);
      }
    }

    succ &= DataWriter.writeWithIndex(this.succs,
            this.key + "_SUCC_RATIOS", folder);
    succ &= DataWriter.writeWithIndex(this.stab,
            this.key + "_STABILIZATION", folder);

    double[][] s1 = new double[this.roots.length][];
    double[][] s2 = new double[this.roots.length][];
    double[][] s3 = new double[this.roots.length][];
    double[] av1 = new double[this.roots.length];
    double[] av2 = new double[this.roots.length];
    double[] av3 = new double[this.roots.length];
    for (int i = 0; i < s1.length; i++) {
      s1[i] = this.pathsPerTree[i].getDistribution();
      av1[i] = this.pathsPerTree[i].getAverage();
      s2[i] = this.pathsPerTreeFound[i].getDistribution();
      av2[i] = this.pathsPerTreeFound[i].getAverage();
      s3[i] = this.pathsPerTreeNF[i].getDistribution();
      av3[i] = this.pathsPerTreeNF[i].getAverage();
    }

    succ &= DataWriter.writeWithIndex(av1, this.key + "_PATH_PERTREE_AV", folder);
    succ &= DataWriter.writeWithIndex(av2, this.key + "_PATH_PERTREE_FOUND_AV", folder);
    succ &= DataWriter.writeWithIndex(av3, this.key + "_PATH_PERTREE_NF_AV", folder);

    succ &= safeWriteWithoutIndex(s1, "_PATH_PERTREE", folder);
    succ &= safeWriteWithoutIndex(s2, "_PATH_PERTREE", folder);
    succ &= safeWriteWithoutIndex(s3, "_PATH_PERTREE_FOUND", folder);
    //succ &= DataWriter.writeWithoutIndex(s1, this.key + "_PATH_PERTREE", folder);
    //succ &= DataWriter.writeWithoutIndex(s2, this.key + "_PATH_PERTREE_FOUND", folder);
//    for (double[] doubles : s3) {
//      if (doubles == null) {
//        break;
//      }
//      try {
//        succ &= DataWriter.writeWithoutIndex(s3, this.key + "_PATH_PERTREE_NF", folder);
//      } catch (NullPointerException e) {
//        e.printStackTrace();
//      }
//    }

    succ &= DataWriter.writeWithIndex(this.passRoot, this.key + "_ROOT_TRAF", folder);

    if (Config.getBoolean("SERIES_GRAPH_WRITE")) {
      (new GtnaGraphWriter()).writeWithProperties(graph, folder + "graph.txt");
    }


    return succ;
  }

  private boolean safeWriteWithoutIndex(double[][] in, String keyString, String folder) {
    for (double[] doubles: in) {
      if (doubles == null) {
        return false;
      }
    }

    return DataWriter.writeWithoutIndex(in, this.key + keyString, folder);
  }

  @Override
  public Single[] getSingles() {
    List<Single> l = new ArrayList<>(20);
    for (String dataKey: distributions.keySet()) {
      l.add(new Single(SINGLE_NAMES.get(dataKey), distributions.get(dataKey).getAverage()));
    }
    Single[] singles = l.toArray(new Single[20]);

    singles[16] = new Single("CREDIT_NETWORK_STAB_AV", this.stab_av);
    singles[17] = new Single("CREDIT_NETWORK_ROOT_TRAF_AV", this.passRootAll);
    singles[18] = new Single("CREDIT_NETWORK_SUCCESS_DIRECT", this.success_first);
    singles[19] = new Single("CREDIT_NETWORK_SUCCESS", this.success);

    //return new Single[]{m_av, m_Re_av, m_S_av, m_F_av, p_av, p_Re_av, p_S_av, p_F_av,
    //reL_av, ls_av, s_av, s1, s, pP_av, pPF_av, pPNF_av, rt, d1, d2, d3};
    return singles;
  }

  @Override
  public boolean applicable(Graph g, Network n, HashMap<String, Metric> m) {
    return g.hasProperty("CREDIT_LINKS");
  }

  private Vector<Transaction> readList(String file) {
    Vector<Transaction> vec = new Vector<Transaction>();
    try {
      BufferedReader br = new BufferedReader(new FileReader(file));
      String line;
      int count = 0;
      while ((line = br.readLine()) != null) {
        String[] parts = line.split(" ");
        if (parts.length == 4) {
          Transaction ta = new Transaction(Double.parseDouble(parts[0]),
                  Double.parseDouble(parts[1]),
                  Integer.parseInt(parts[2]),
                  Integer.parseInt(parts[3]));
          vec.add(ta);
        }
        if (parts.length == 3) {
          Transaction ta = new Transaction(count,
                  Double.parseDouble(parts[0]),
                  Integer.parseInt(parts[1]),
                  Integer.parseInt(parts[2]));
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

  private LinkedList<double[]> readLinks(String file) {
    LinkedList<double[]> vec = new LinkedList<>();
    try {
      BufferedReader br = new BufferedReader(new FileReader(file));
      String line;
      while ((line = br.readLine()) != null) {
        String[] parts = line.split(" ");
        if (parts.length == 4) {
          double[] link = new double[4];
          for (int i = 0; i < parts.length; i++) {
            link[i] = Double.parseDouble(parts[i]);
          }
          vec.add(link);
        }

      }
      br.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
    return vec;
  }

  private void incrementCount(List<Long> values, int index) {
    if (index < values.size()) {
      values.set(index, values.get(index) + 1);
    } else {
      for (int i = values.size(); i <= index; i++) {
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

  private void incrementCount(String propName, int index) {
    List<Long> values = longMetrics.get(propName);
    incrementCount(values, index);
  }

  private void setRoots(int[][] paths) {
    for (int i = 0; i < paths.length; i++) {
      if (paths[i] == null) continue;
      this.rootPath++;
      for (int j = 0; j < paths[i].length; j++) {
        if (paths[i][j] == this.roots[i]) {
          this.passRootAll++;
          this.passRoot[i]++;
        }
      }
    }
  }
}
