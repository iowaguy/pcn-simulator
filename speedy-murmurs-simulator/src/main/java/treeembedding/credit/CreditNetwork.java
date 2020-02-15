package treeembedding.credit;

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
import java.util.Vector;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import gtna.data.Single;
import gtna.graph.Edge;
import gtna.graph.Edges;
import gtna.graph.Graph;
import gtna.graph.Node;
import gtna.graph.spanningTree.SpanningTree;
import gtna.io.DataWriter;
import gtna.metrics.Metric;
import gtna.networks.Network;
import gtna.transformation.spanningtree.MultipleSpanningTree;
import gtna.transformation.spanningtree.MultipleSpanningTree.Direct;
import gtna.util.Distribution;
import gtna.util.parameter.BooleanParameter;
import gtna.util.parameter.DoubleParameter;
import gtna.util.parameter.IntParameter;
import gtna.util.parameter.Parameter;
import gtna.util.parameter.StringParameter;
import treeembedding.RoutingAlgorithm;
import treeembedding.RunConfig;
import treeembedding.byzantine.AttackType;
import treeembedding.credit.exceptions.InsufficientFundsException;
import treeembedding.credit.exceptions.TransactionFailedException;
import treeembedding.credit.partioner.Partitioner;
import treeembedding.treerouting.NextHopPlusMetrics;
import treeembedding.treerouting.TreeCoordinates;
import treeembedding.treerouting.Treeroute;
import treeembedding.treerouting.TreerouteSilentW;
import treeembedding.vouteoverlay.Treeembedding;

public class CreditNetwork extends AbstractCreditNetworkBase {
  //input parameters
  private final Treeroute ra; //routing algorithm
  private final boolean dynRepair; //true if topology changes are immediately fixed rather than recomputation each epoch
  private final boolean multi; //using multi-party computation to determine minimum or do routing adhoc
  private final double requeueInt; //interval until a failed transaction is re-tried; irrelevant if !dynRepair as
  //retry is start of next epoch
  private final Partitioner part; //method to partition overall transaction value on paths
  private final int[] roots; // spanning tree roots
  private final int maxTries;
  private Queue<double[]> newLinks;

  private final boolean update;

  //computed metrics
  private double stab_av; //average stab overhead


  private double passRootAll = 0;
  private int rootPath = 0;

  private final ExecutorService executor;

  private RoutingAlgorithm algo;

  public CreditNetwork(String file, String name, double epoch, RoutingAlgorithm algo,
                       double requeueInt, Partitioner part, int[] roots, int max, String links,
                       boolean up, RunConfig runConfig) {
    super("CREDIT_NETWORK", new Parameter[]{new StringParameter("NAME", name), new DoubleParameter("EPOCH", epoch),
            new StringParameter("RA", algo.getTreeroute().getKey()), new BooleanParameter("DYN_REPAIR", algo.doesDynamicRepair()),
            new BooleanParameter("MULTI", algo.usesMPC()), new IntParameter("TREES", roots.length),
            new DoubleParameter("REQUEUE_INTERVAL", requeueInt), new StringParameter("PARTITIONER", part.getName()),
            new IntParameter("MAX_TRIES", max)}, epoch, roots.length, file, runConfig);

    this.ra = algo.getTreeroute();
    this.multi = algo.usesMPC();
    this.dynRepair = algo.doesDynamicRepair();
    this.algo = algo;

    this.requeueInt = requeueInt;
    this.part = part;
    this.roots = roots;
    this.maxTries = max;
    if (links != null) {
      this.newLinks = this.readLinks(links);
    } else {
      this.newLinks = new LinkedList<>();
    }
    this.update = up;

    this.zeroEdges = new ConcurrentLinkedQueue<>();



    int threads = 1;
    if (runConfig.areTransactionsConcurrent()) {
      threads = runConfig.getConcurrentTransactionsCount();
    }
    executor = Executors.newFixedThreadPool(threads);
  }

  public CreditNetwork(String file, String name, double epoch, RoutingAlgorithm algo,
                       double requeueInt, Partitioner part, int[] roots, int max, String links,
                       RunConfig runConfig) {
    this(file, name, epoch, algo, requeueInt, part, roots, max, links, true, runConfig);
  }

  public CreditNetwork(String file, String name, double epoch, RoutingAlgorithm algo,
                       double requeueInt, Partitioner part, int[] roots, int max, boolean up,
                       RunConfig runConfig) {
    this(file, name, epoch, algo, requeueInt, part, roots, max, null, up, runConfig);
  }

  private Future<TransactionResults> transactionResultsFuture(Transaction cur, Graph g, Node[] nodes,
                                                              CreditLinks edgeweights, int currentEpoch) {
    return executor.submit(() -> transact(cur, g, nodes, edgeweights, currentEpoch));
  }

  private TransactionResults transact(Transaction currentTransaction, Graph g, Node[] nodes,
                                      CreditLinks edgeweights, int currentEpoch) {
    currentTransaction.startTime = (double) System.nanoTime();
    TransactionResults results = null;
    boolean[] exclude = new boolean[nodes.length];
    try {
      // execute the transaction
      if (this.multi) {
        results = this.routeMulti(currentTransaction, g, nodes, exclude, edgeweights);
      } else {
        results = this.routeAdhoc(currentTransaction, g, nodes, exclude, edgeweights);
      }
    } catch (TransactionFailedException e) {
      results = new TransactionResults();
      results.setSuccess(false);
      log.error(e.getMessage());
    }
    currentTransaction.endTime = (double) System.nanoTime();
//    results.setTx(currentTransaction);

    currentTransaction.addPath(results.getSumPathLength());
    currentTransaction.addMes(results.getRes4());

    //re-queue if necessary
    if (!results.isSuccess()) {
      Random rand = new Random();
      currentTransaction.incRequeue(currentTransaction.time + rand.nextDouble() * this.requeueInt);
      if (currentTransaction.timesRequeued < (this.maxTries - 1)) {
        toRetry.add(currentTransaction);
      } else {
        incrementCount(Metrics.MESSAGES_ALL, currentTransaction.mes);
        incrementCount(Metrics.PATHS_ALL, currentTransaction.path);
      }
    }

    if (!this.update) {
      this.weightUpdate(edgeweights, results.getModifiedEdges());
    }

    calculateMetrics(results, currentTransaction, currentEpoch);
    return results;
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

  private CreditLinks generateCreditLinks(Graph g) {
    CreditLinks edgeweights = (CreditLinks) g.getProperty("CREDIT_LINKS");
    edgeweights.setCollateralization(algo.collateralizationType());
    return edgeweights;
  }

  @Override
  public void computeData(Graph g, Network n, HashMap<String, Metric> m) {
    this.graph = g;
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
    Node[] nodes = g.getNodes();

    // generate byzantine nodes
    this.byzantineNodes = this.attack.generateAttackers(nodes, this.transactions);

    int lastEpoch = 0;
    int stabilizationMessages = 0;
    Queue<Future<TransactionResults>> pendingTransactions = new LinkedList<>();

    edgeweights = generateCreditLinks(g);
    edgeweights.setCollateralization(algo.collateralizationType());

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
      }

      // collect result futures
      Future<TransactionResults> futureResults = transactionResultsFuture(currentTransaction, g, nodes, edgeweights, currentEpoch);
      pendingTransactions.add(futureResults);

      //4 post-processing: remove edges set to 0, update spanning tree if dynRepair
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
                      cut, edgeweights);
            }
          }
        }
      }
    }
    this.executor.shutdown();
    // don't want metrics to be computed before all transactions are done
    try {
      if (!this.executor.awaitTermination(5, TimeUnit.HOURS)) {
        log.error("Maximum time elapsed waiting for transactions to complete.");
        this.executor.shutdownNow();
      }
    } catch (InterruptedException e) {
      e.printStackTrace();
    }

    if (this.dynRepair) {
      stabMes.add(stabilizationMessages);
    }

    //compute metrics
    distributions.put(Metrics.PATH, new Distribution(convertListToLongArray(Metrics.PATH), totalTransactionAttemptCount));
    distributions.put(Metrics.MESSAGES, new Distribution(convertListToLongArray(Metrics.MESSAGES), totalTransactionAttemptCount));
    distributions.put(Metrics.PATHS_ALL, new Distribution(convertListToLongArray(Metrics.PATHS_ALL), transactions.size()));
    distributions.put(Metrics.MESSAGES_ALL, new Distribution(convertListToLongArray(Metrics.MESSAGES_ALL), transactions.size()));
    distributions.put(Metrics.PATH_SUCCESS, new Distribution(convertListToLongArray(Metrics.PATH_SUCCESS), (int) this.success));
    distributions.put(Metrics.MESSAGES_SUCCESS, new Distribution(convertListToLongArray(Metrics.MESSAGES_SUCCESS), (int) this.success));
    distributions.put(Metrics.PATH_FAIL, new Distribution(convertListToLongArray(Metrics.PATH_FAIL), transactions.size() - (int) this.success));
    distributions.put(Metrics.MESSAGES_FAIL, new Distribution(convertListToLongArray(Metrics.MESSAGES_FAIL), transactions.size() - (int) this.success));
    distributions.put(Metrics.RECEIVER_LANDMARK_MESSAGES, new Distribution(convertListToLongArray(Metrics.RECEIVER_LANDMARK_MESSAGES), totalTransactionAttemptCount));
    distributions.put(Metrics.LANDMARK_SENDER_MESSAGES, new Distribution(convertListToLongArray(Metrics.LANDMARK_SENDER_MESSAGES), totalTransactionAttemptCount));
    distributions.put(Metrics.ATTEMPTS, new Distribution(convertListToLongArray(Metrics.ATTEMPTS), (int) this.success));
    distributions.put(Metrics.SINGLE_PATHS, new Distribution(convertListToLongArray(Metrics.SINGLE_PATHS), cAllPath.get(0) + cAllPath.get(1)));
    distributions.put(Metrics.SINGLE_PATHS_DEST_FOUND, new Distribution(convertListToLongArray(Metrics.SINGLE_PATHS_DEST_FOUND), cAllPath.get(0)));
    distributions.put(Metrics.SINGLE_PATHS_DEST_NOT_FOUND, new Distribution(convertListToLongArray(Metrics.SINGLE_PATHS_DEST_NOT_FOUND), cAllPath.get(1)));
    distributions.put(Metrics.DELAY, new Distribution(convertListToLongArray(Metrics.DELAY), totalTransactionAttemptCount));
    distributions.put(Metrics.DELAY_SUCCESS, new Distribution(convertListToLongArray(Metrics.DELAY_SUCCESS), (int) this.success));
    distributions.put(Metrics.DELAY_FAIL, new Distribution(convertListToLongArray(Metrics.DELAY_FAIL), totalTransactionAttemptCount - (int) this.success));

    this.pathsPerTree = new Distribution[this.roots.length];
    this.pathsPerTreeFound = new Distribution[this.roots.length];
    this.pathsPerTreeNF = new Distribution[this.roots.length];
    for (int j = 0; j < roots.length; j++) {
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

    calculatePerEpochRatios();

    this.stab_av = this.stab_av / (double) stab.length;
    this.passRootAll = this.passRootAll / this.rootPath;
    this.graph = g;
  }

  private int computeNonZeroEdges(Graph g, CreditLinks ew) {
    Edges edges = g.getEdges();
    int c = 0;
    for (Edge e : edges.getEdges()) {
      if (e.getSrc() < e.getDst()) {
        if (ew.getMaxTransactionAmount(e.getSrc(), e.getDst()) > 0 ||
                ew.getMaxTransactionAmount(e.getDst(), e.getSrc()) > 0) {
          c++;
        }
      }
    }
    return c;
  }

  /**
   * reconnect disconnected branch with root subroot
   */
  private synchronized int repairTree(Node[] nodes, SpanningTree spanningTree, TreeCoordinates coords, int subroot, CreditLinks edgeweights) {
    if (!update) {
      int mes = 0;
      Queue<Integer> q1 = new LinkedList<>();
      q1.add(subroot);
      while (!q1.isEmpty()) {
        int node = q1.poll();
        int[] kids = spanningTree.getChildren(node);
        for (int kid : kids) {
          mes++;
          q1.add(kid);
        }
        mes = mes + MultipleSpanningTree.potentialParents(graph, nodes[node],
                Direct.EITHER, edgeweights).length;
      }
      return mes;
    }
    // remove each node of subtree from spanning tree
    int mes = 0;
    Queue<Integer> nodesToRemoveFromSpanningTree = new LinkedList<>();
    Queue<Integer> nodesToAddBackToSpanningTree = new LinkedList<>();
    nodesToRemoveFromSpanningTree.add(subroot);
    while (!nodesToRemoveFromSpanningTree.isEmpty()) {
      int removeThisNode = nodesToRemoveFromSpanningTree.poll();
      int[] childrenToRemove = spanningTree.getChildren(removeThisNode);
      for (int childToRemove : childrenToRemove) {
        mes++;
        nodesToRemoveFromSpanningTree.add(childToRemove);
      }
      spanningTree.removeNode(removeThisNode);
      nodesToAddBackToSpanningTree.add(removeThisNode);
    }

    Random rand = new Random();

    // first find parents with which the new node has a bidrectional link, if those don't exist,
    // choose parents with which the new node has a unidirectional link
    MultipleSpanningTree.Direct[] directions = {Direct.BOTH, Direct.EITHER, Direct.NONE};
    Queue<Integer> nodesToRetry = new LinkedList<>();
    for (Direct direction : directions) {
      while (!nodesToRetry.isEmpty()) {
        int retryMe = nodesToRetry.poll();
        nodesToAddBackToSpanningTree.add(retryMe);
      }

      while (!nodesToAddBackToSpanningTree.isEmpty()) {
        int addMeToSpanningTree = nodesToAddBackToSpanningTree.poll();
        Vector<Integer> bestPotentialParents = new Vector<>();
        int minDepth = Integer.MAX_VALUE;

        // narrow down list of best potential parents
        int[] potentialParents = MultipleSpanningTree.potentialParents(graph, nodes[addMeToSpanningTree],
                direction, edgeweights);
        for (int potentialParent : potentialParents) {
          if (spanningTree.getParent(potentialParent) != -2) {
            int curDepth = spanningTree.getDepth(potentialParent);
            if (curDepth < minDepth) {
              minDepth = curDepth;
              bestPotentialParents = new Vector<>();
            }
            if (curDepth == minDepth) {
              bestPotentialParents.add(potentialParent);
            }
          }
        }


        if (!bestPotentialParents.isEmpty()) {
          mes = mes + MultipleSpanningTree.potentialParents(graph, nodes[addMeToSpanningTree],
                  Direct.EITHER, edgeweights).length;

          // choose a random parent from among the list of best possibilities
          int chosenParent = bestPotentialParents.get(rand.nextInt(bestPotentialParents.size()));

          // add child to parent in the spanning tree
          spanningTree.addParentChild(chosenParent, addMeToSpanningTree);
          long[] parentCoords = coords.getCoord(chosenParent);
          long[] childCoords = new long[parentCoords.length + 1];
          System.arraycopy(parentCoords, 0, childCoords, 0, parentCoords.length);
          childCoords[parentCoords.length] = rand.nextInt();
          coords.setCoord(addMeToSpanningTree, childCoords);
        } else {
          nodesToRetry.add(addMeToSpanningTree);
        }
      }
    }
    return mes;
  }

  private int addLink(int src, int dst, double weight, Graph g) {
    CreditLinks edgeweights = generateCreditLinks(g);
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
            st = st + repairTree(nodes, sp, coords, src, edgeweights);
          }
          if (zpDst) {
            TreeCoordinates coords = (TreeCoordinates) g.getProperty("TREE_COORDINATES_" + j);
            st = st + repairTree(nodes, sp, coords, dst, edgeweights);
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
            st = st + repairTree(nodes, sp, coords, cut, edgeweights);
          }

        }
      }
    }
    return st;
  }

  private boolean isZeroPath(SpanningTree sp, int node, CreditLinks edgeweights) {
    int parent = sp.getParent(node);
    while (parent != -1) {
      if (edgeweights.getMaxTransactionAmount(node, parent) > 0 &&
              edgeweights.getMaxTransactionAmount(parent, node) > 0) {
        node = parent;
        parent = sp.getParent(node);
      } else {
        return true;
      }
    }
    return false;
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
      NextHopPlusMetrics n = ra.getRoute(src, dest, treeIndex, g, nodes, exclude);
      paths[treeIndex] = n.getPath();
      addPerEpochValue(Metrics.BLOCKED_LINKS_PER_EPOCH, (double) n.getBlockedLinks(), calculateEpoch(cur));

      if (paths[treeIndex][paths[treeIndex].length - 1] == dest) {
        int currentNodeIndex = src;
        double currentMaxForPath = Double.MAX_VALUE;
        for (int nodeIndex = 1; nodeIndex < paths[treeIndex].length; nodeIndex++) {
          int nextNodeIndex = paths[treeIndex][nodeIndex];
          double maxTransactionAmount = edgeweights.getMaxTransactionAmount(currentNodeIndex,
                  nextNodeIndex);
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
    TransactionStepResult transactionStepResult = stepThroughTransaction(cur, vals, nodes, g,
            exclude, edgeweights, modifiedEdges);

    if (transactionStepResult != null) {
      Map<Edge, List<Double>> edgeModifications = transactionStepResult.getEdgeModifications();
      finalizeTransaction(cur, vals, paths, edgeweights, edgeModifications);
    }

    //compute metrics
    TransactionResults res = new TransactionResults();

    //success
    res.setSuccess(transactionStepResult != null);

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
    int[][] paths = null;
    int src = cur.src;
    int dest = cur.dst;
    //distribute values on paths
    double[] vals = this.part.partition(g, src, dest, cur.val, roots.length);

    //check if transaction works
    Map<Edge, LinkWeight> modifiedEdges = new HashMap<>();
    TransactionStepResult transactionStepResult = stepThroughTransaction(cur, vals, nodes, g,
            exclude, edgeweights, modifiedEdges);

    if (transactionStepResult != null) {
      paths = transactionStepResult.getPaths();
      Map<Edge, List<Double>> edgeModifications = transactionStepResult.getEdgeModifications();
      // will only enter here if transaction was successful
      finalizeTransaction(cur, vals, transactionStepResult.getPaths(), edgeweights, edgeModifications);
    }

    //compute metrics
    TransactionResults res = new TransactionResults();

    //success
    res.setSuccess(transactionStepResult != null);

    res.setModifiedEdges(modifiedEdges);

    //path length
    for (int j = 0; paths != null && j < paths.length; j++) {
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
    for (int j = 0; paths != null && j < paths.length; j++) {
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
    if (paths != null) {
      this.setRoots(paths);
    }
    return res;
  }

  private void weightUpdate(CreditLinks edgeweights, Map<Edge, LinkWeight> updatedEdges) {
    for (Entry<Edge, LinkWeight> entry : updatedEdges.entrySet()) {
      edgeweights.setWeight(entry.getKey(), entry.getValue());
    }
  }

  /**
   * Step through transaction one hop at a time, and returns its success status
   *
   * @return a map of lists of all the weight changes, and the edges they correspond to. A single
   *         edge can have multiple weight changes.
   */
  private TransactionStepResult stepThroughTransaction(Transaction cur, double[] vals,
                                                       Node[] nodes, Graph g, boolean[] exclude,
                                                       CreditLinks edgeweights, Map<Edge, LinkWeight> modifiedEdges) {
    Map<Edge, List<Double>> edgeModifications = new HashMap<>();
    if (vals == null) {
      transactionFailed(edgeweights, edgeModifications);
      return null;
    }

    int[][] paths = new int[roots.length][];
    for (int pathIndex = 0; pathIndex < roots.length; pathIndex++) {
      if (vals[pathIndex] != 0) {
        int s = cur.src;
        int d = cur.dst;
        if (vals[pathIndex] < 0) {
          s = cur.dst;
          d = cur.src;
        }
        NextHopPlusMetrics n = this.ra.getRoute(s, d, pathIndex, g, nodes, exclude, edgeweights, vals[pathIndex]);
        paths[pathIndex] = n.getPath();
        addPerEpochValue(Metrics.BLOCKED_LINKS_PER_EPOCH, n.getBlockedLinks(), calculateEpoch(cur));

        if (paths[pathIndex][paths[pathIndex].length - 1] == -1) {
          // could not find a path
          transactionFailed(edgeweights, edgeModifications);
          return null;
        }

        // simulate routing
        int currentNodeId = paths[pathIndex][0];
        for (int nodeIndex = 1; nodeIndex < paths[pathIndex].length; nodeIndex++) {
          simulateNetworkLatency();

          // Attack logic
          if (attack != null && attack.getType() == AttackType.DROP_ALL) {
            if (this.byzantineNodes.contains(paths[pathIndex][nodeIndex])) {
              // do byzantine action
              transactionFailed(edgeweights, edgeModifications);
              return null;
            }
          }

          int nextNodeId = paths[pathIndex][nodeIndex];
          Edge edge = CreditLinks.makeEdge(currentNodeId, nextNodeId);
          LinkWeight weights = edgeweights.getWeights(edge);

          try {
            if (log.isInfoEnabled()) {
              log.info("Prepare: cur=" + currentNodeId + "; next=" + nextNodeId + "; val=" + vals[pathIndex]);
            }
            edgeweights.prepareUpdateWeight(currentNodeId, nextNodeId, vals[pathIndex]);

            if (!modifiedEdges.containsKey(edge)) {
              modifiedEdges.put(edge, weights);
            }

            double currentVal = vals[pathIndex];
            List<Double> base = new LinkedList<>();
            if (currentNodeId > nextNodeId) {
              base.add(-currentVal);
            } else {
              base.add(currentVal);
            }
            int finalCurrentNodeId = currentNodeId;
            edgeModifications.merge(edge, base, (l, ignore) -> {
              if (finalCurrentNodeId > nextNodeId) {
                l.add(-currentVal);
              } else {
                l.add(currentVal);
              }
              return l;
            });

          } catch (InsufficientFundsException e) {
            transactionFailed(edgeweights, edgeModifications);
            return null;
          }

          currentNodeId = nextNodeId;
        }
      }
    }

    return new TransactionStepResult(paths, edgeModifications);
  }

  private void finalizeTransaction(Transaction currentTransaction, double[] vals, int[][] paths,
                                   CreditLinks edgeweights, Map<Edge,
          List<Double>> edgeModifications) throws TransactionFailedException {
    if (vals == null) {
      throw new TransactionFailedException("Transaction values cannot be null");
    }

    // payment griefing attack logic
    for (int[] path : paths) {
      if (attack != null && (attack.getType() == AttackType.GRIEFING ||
              attack.getType() == AttackType.GRIEFING_SUCCESS)) {
        int destination = path[path.length - 1];
        if (this.byzantineNodes.contains(destination)) {
          try {
            Thread.sleep(attack.getReceiverDelayMs());
          } catch (InterruptedException e) {
            // don't worry about it
          }

          if (attack.getType() == AttackType.GRIEFING) {
            transactionFailed(edgeweights, edgeModifications);
            throw new TransactionFailedException("This payment was griefed");
          }

        }
      }
    }

    for (int treeIndex = 0; treeIndex < paths.length; treeIndex++) {
      if (vals[treeIndex] != 0) {
        int currentNodeIndex = paths[treeIndex][0];
        for (int nodeIndex = 1; nodeIndex < paths[treeIndex].length; nodeIndex++) {
          simulateNetworkLatency();

          int nextNodeIndex = paths[treeIndex][nodeIndex];
          Edge edge = CreditLinks.makeEdge(currentNodeIndex, nextNodeIndex);

          if (log.isInfoEnabled()) {
            log.info("Finalize: cur=" + currentNodeIndex + "; next=" + nextNodeIndex + "; val=" + vals[treeIndex]);
          }
          finalizeUpdateWeight(currentNodeIndex, nextNodeIndex, vals[treeIndex],
                  calculateEpoch(currentTransaction));

          if (edgeModifications.containsKey(edge)) {
            log.debug("Removing updated edge from set");
            edgeModifications.get(edge).remove(vals[treeIndex]);
          }

          if (runConfig.getRoutingAlgorithm() != RoutingAlgorithm.MAXFLOW &&
                  edgeweights.isZero(currentNodeIndex, nextNodeIndex)) {
            this.zeroEdges.add(edge);
          }
          currentNodeIndex = nextNodeIndex;

        }
      }
    }
  }

  @Override
  public Single[] getSingles() {
    List<Single> l = new ArrayList<>(20);
    for (Metrics dataKey : distributions.keySet()) {
      l.add(new Single("CREDIT_NETWORK" + dataKey.getSingleName(), distributions.get(dataKey).getAverage()));
    }
    Single[] singles = l.toArray(new Single[20]);

    singles[16] = new Single("CREDIT_NETWORK_STAB_AV", this.stab_av);
    singles[17] = new Single("CREDIT_NETWORK_ROOT_TRAF_AV", this.passRootAll);
    singles[18] = new Single("CREDIT_NETWORK_SUCCESS_DIRECT", this.success_first);
    singles[19] = new Single("CREDIT_NETWORK_SUCCESS", this.success);

    return singles;
  }

  @Override
  public boolean applicable(Graph g, Network n, HashMap<String, Metric> m) {
    return g.hasProperty("CREDIT_LINKS");
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

  @Override
  public boolean writeData(String folder) {
    boolean succ = DataWriter.writeWithIndex(this.stab,
            this.key + "_STABILIZATION", folder);

    double[][] s1 = new double[numRoots][];
    double[][] s2 = new double[numRoots][];
    double[][] s3 = new double[numRoots][];
    double[] av1 = new double[numRoots];
    double[] av2 = new double[numRoots];
    double[] av3 = new double[numRoots];
    for (int i = 0; i < numRoots; i++) {
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
    succ &= safeWriteWithoutIndex(s2, "_PATH_PERTREE_FOUND", folder);
    succ &= safeWriteWithoutIndex(s3, "_PATH_PERTREE_NF", folder);

    succ &= DataWriter.writeWithIndex(this.passRoot, this.key + "_ROOT_TRAF", folder);

    succ &= writeDataCommon(folder);

    return succ;
  }

  private class TransactionStepResult {
    int[][] paths;
    Map<Edge, List<Double>> edgeModifications;

    TransactionStepResult(int[][] paths, Map<Edge, List<Double>> edgeModifications) {
      this.paths = paths;
      this.edgeModifications = edgeModifications;
    }

    int[][] getPaths() {
      return paths;
    }

    Map<Edge, List<Double>> getEdgeModifications() {
      return edgeModifications;
    }
  }
}
