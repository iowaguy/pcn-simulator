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
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import gtna.data.Single;
import gtna.graph.Edge;
import gtna.graph.Graph;
import gtna.graph.Node;
import gtna.metrics.Metric;
import gtna.networks.Network;
import gtna.util.Distribution;
import gtna.util.parameter.DoubleParameter;
import gtna.util.parameter.IntParameter;
import gtna.util.parameter.Parameter;
import treeembedding.RunConfig;


public class CreditMaxFlow extends AbstractCreditNetworkBase {
  //input parameters

  //interval until a failed transaction is re-tried; irrelevant if !dynRepair b/c
  // retry is start of next epoch
  private double requeueInt;
  private int maxTries;
  private Queue<double[]> newLinks;
  private boolean update;
  private ExecutorService executor;

  private RunConfig runConfig;
  private Set<Integer> byzantineNodes;
  private CreditLinks edgeweights;

  public CreditMaxFlow(String file, String name, double requeueInt,
                       int max, String links, boolean up, double epoch, RunConfig runConfig) {
    super(CREDIT_MAX_FLOW, new Parameter[]{
            new DoubleParameter("REQUEUE_INTERVAL", requeueInt),
            new IntParameter("MAX_TRIES", max)}, epoch, 1, file, runConfig);
    this.requeueInt = requeueInt;
    this.maxTries = max;
    if (links != null) {
      this.newLinks = this.readLinks(links);
    } else {
      this.newLinks = new LinkedList<>();
    }
    this.update = up;
    this.runConfig = runConfig;
    int threads = 1;
    if (runConfig.areTransactionsConcurrent()) {
      threads = runConfig.getConcurrentTransactionsCount();
    }
    executor = Executors.newFixedThreadPool(threads);
    this.distributions = new HashMap<>();
  }

  public CreditMaxFlow(String file, String name, double requeueInt, int max,
                       boolean up, double epoch, RunConfig runConfig) {
    this(file, name, requeueInt, max, null, up, epoch, runConfig);
  }

  public CreditMaxFlow(String file, String name, double requeueInt, int max,
                       String links, double epoch, RunConfig runConfig) {
    this(file, name, requeueInt, max, links, true, epoch, runConfig);
  }

  @Override
  public void computeData(Graph g, Network n, HashMap<String, Metric> m) {
    this.graph = g;
    int totalTransactionAttemptCount = 0;

    success_first = 0;
    success = 0;
    Vector<Double> succR = new Vector<>();
    Vector<Integer> stabMes = new Vector<>();
    Node[] nodes = g.getNodes();
    boolean[] exclude = new boolean[nodes.length];

    // generate byzantine nodes
    this.byzantineNodes = runConfig.getAttackProperties().generateAttackers(nodes);
    Queue<Future<TransactionResults>> pendingTransactions = new LinkedList<>();

    edgeweights = (CreditLinks) g.getProperty("CREDIT_LINKS");
    edgeweights.enableFundLocking(runConfig.getRoutingAlgorithm().isFundLockingEnabled());

    //go over transactions
    toRetry = new LinkedList<>();
    int lastEpoch = 0;
    //Random rand = new Random();
    while (areTransactionsAvailable()) {
      Transaction currentTransaction = getNextTransaction();

      totalTransactionAttemptCount++;
      if (log.isInfoEnabled()) {
        log.info(currentTransaction.toString());
      }

      // calculate epoch
      int currentEpoch = calculateEpoch(currentTransaction);


      // TODO this calculation won't make any sense if transactions are async, they will complete too quickly so the succ ratios will be way off
      if (currentEpoch != lastEpoch) {
        double cur_succd = cur_count == 0 ? 1 : (double) cur_succ / (double) cur_count;
        succR.add(cur_succd);
        for (int j = lastEpoch + 2; j <= currentEpoch; j++) {
          succR.add(1.0);
        }
        cur_count = 0;
        cur_succ = 0;
      }

      // collect result futures
      Future<TransactionResults> futureResults = transactionResultsFuture(currentTransaction, g);
      pendingTransactions.add(futureResults);

      lastEpoch = currentEpoch;
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

    //compute metrics
    distributions.put(PATH, new Distribution(convertListToLongArray(PATH), totalTransactionAttemptCount));
    distributions.put(MESSAGES, new Distribution(convertListToLongArray(MESSAGES), totalTransactionAttemptCount));
    distributions.put(PATHS_ALL, new Distribution(convertListToLongArray(PATHS_ALL), transactions.size()));
    distributions.put(MESSAGES_ALL, new Distribution(convertListToLongArray(MESSAGES_ALL), transactions.size()));
    distributions.put(PATH_SUCCESS, new Distribution(convertListToLongArray(PATH_SUCCESS), (int) this.success));
    distributions.put(MESSAGES_SUCCESS, new Distribution(convertListToLongArray(MESSAGES_SUCCESS), (int) this.success));
    distributions.put(PATH_FAIL, new Distribution(convertListToLongArray(PATH_FAIL), transactions.size() - (int) this.success));
    distributions.put(MESSAGES_FAIL, new Distribution(convertListToLongArray(MESSAGES_FAIL), transactions.size() - (int) this.success));
    distributions.put(ATTEMPTS, new Distribution(convertListToLongArray(ATTEMPTS), (int) this.success));
    distributions.put(SINGLE_PATHS, new Distribution(convertListToLongArray(SINGLE_PATHS), cAllPath.get(0) + cAllPath.get(1)));
    distributions.put(SINGLE_PATHS_DEST_FOUND, new Distribution(convertListToLongArray(SINGLE_PATHS_DEST_FOUND), cAllPath.get(0)));
    distributions.put(SINGLE_PATHS_DEST_NOT_FOUND, new Distribution(convertListToLongArray(SINGLE_PATHS_DEST_NOT_FOUND), cAllPath.get(1)));

    this.success = this.success / (double) transactions.size();
    this.success_first = this.success_first / (double) transactions.size();
    this.graph = g;
    this.succs = new double[succR.size()];

    for (int i = 0; i < this.succs.length; i++) {
      succs[i] = succR.get(i);
    }
  }

  @Override
  public boolean writeData(String folder) {
    return writeDataCommon(folder);
  }

  private Future<TransactionResults> transactionResultsFuture(Transaction cur, Graph g) {
    return executor.submit(() -> transact(cur, g));
  }

  private TransactionResults transact(Transaction currentTransaction, Graph g) {
    TransactionResults results = fordFulkerson(currentTransaction, g);
    Random rand = new Random();

    //re-queue if necessary
    if (!results.isSuccess()) {
      currentTransaction.incRequeue(currentTransaction.time + rand.nextDouble() * this.requeueInt);
      if (currentTransaction.requeue <= this.maxTries) {
        toRetry.add(currentTransaction);
      } else {
        incrementCount(MESSAGES_ALL, currentTransaction.mes);
        incrementCount(PATHS_ALL, currentTransaction.path);
      }
    }

    currentTransaction.addPath(results.getSumPathLength());
    currentTransaction.addMes(results.getSumMessages());

    // requeue if necessary
    if (!results.isSuccess()) {
      currentTransaction.incRequeue(currentTransaction.time + rand.nextDouble() * this.requeueInt);
      if (currentTransaction.requeue < this.maxTries) {
        toRetry.add(currentTransaction);
      } else {
        incrementCount(MESSAGES_ALL, currentTransaction.mes);
        incrementCount(PATHS_ALL, currentTransaction.path);
      }
    }

    //3 update metrics accordingly
    calculateMetrics(results, currentTransaction);
//    path = this.inc(path, results[1]);
//    mes = this.inc(mes, results[2]);
//    if (results[0] == 0) {
//      trys = this.inc(trys, currentTransaction.requeue);
//      this.success++;
//      if (currentTransaction.requeue == 0) {
//        this.success_first++;
//      }
//      mesAll = this.inc(mesAll, currentTransaction.mes);
//      pathAll = this.inc(pathAll, currentTransaction.path);
//      pathSucc = this.inc(pathSucc, results[1]);
//      mesSucc = this.inc(mesSucc, results[2]);
//      if (this.writeSucc) {
//        System.out.println("Success: " + currentTransaction.time + " " + currentTransaction.val + " " + currentTransaction.src + " " + currentTransaction.dst);
//      }
//    } else {
//      pathFail = this.inc(pathFail, results[1]);
//      mesFail = this.inc(mesFail, results[2]);
//    }
//    for (int j = 3; j < results.length; j++) {
//      int index = 0;
//      if (results[j] < 0) {
//        index = 1;
//      }
//      cAllPath[index]++;
//      int val = Math.abs(results[j]);
//      pathS = this.inc(pathS, val);
//      if (index == 0) {
//        pathSF = this.inc(pathSF, val);
//      } else {
//        pathSNF = this.inc(pathSNF, val);
//      }
//    }


    return results;
  }

  private TransactionResults fordFulkerson(Transaction currentTransaction, Graph g) {

    Map<Edge, Double> original = new HashMap<>();

    double totalflow = 0;

    // residual paths is a 2d array where first dimension is the path, and second dimension is the messages
    int[][] residualPaths = new int[0][0];
    TransactionResults results = new TransactionResults();

    // loop until a flow has been found for the full transaction amount, or there are no more residual paths
    while (totalflow < currentTransaction.val && (residualPaths = findResidualFlow(edgeweights, g.getNodes(), currentTransaction)).length > 1) {
      if (log.isDebugEnabled()) {
        log.debug("Found residual flow of length " + residualPaths[0].length);
      }

      //potential flow along this path
      double minAlongPath = Double.MAX_VALUE;
      int[] residualPath = residualPaths[0];
      for (int i = 0; i < residualPath.length - 1; i++) {
        double maxTransactionAmount = edgeweights.getMaxTransactionAmount(residualPath[i], residualPath[i + 1]);
        if (maxTransactionAmount < minAlongPath) {
          minAlongPath = maxTransactionAmount;
        }
      }
      //update flows
      minAlongPath = Math.min(minAlongPath, currentTransaction.val - totalflow);
      totalflow = totalflow + minAlongPath;
      for (int i = 0; i < residualPath.length - 1; i++) {
        int n1 = residualPath[i];
        int n2 = residualPath[i + 1];
        double w = edgeweights.getWeight(n1, n2);
        Edge e = n1 < n2 ? new Edge(n1, n2) : new Edge(n2, n1);
        if (!original.containsKey(e)) {
          original.put(e, w);
        }
        if (n1 < n2) {
          edgeweights.setWeight(e, w + minAlongPath);
          if (log.isDebugEnabled()) {
            log.debug("Set weight of (" + n1 + "," + n2 + ") to " + (w + minAlongPath));
          }
        } else {
          edgeweights.setWeight(e, w - minAlongPath);
          if (log.isDebugEnabled()) {
            log.debug("Set weight of (" + n2 + "," + n1 + ") to " + (w - minAlongPath));
          }
        }
      }
      results.addSumMessages(residualPaths[1][0]);
      results.addSumPathLength(residualPath.length - 1);
      results.addPathLength(residualPath.length - 1);
    }

    if (currentTransaction.val - totalflow > 0) {
      //fail
      results.setSuccess(false);

      results.addSumMessages(residualPaths[0][0]);
      //res[2] = res[2] + residualPaths[0][0];
      this.weightUpdate(edgeweights, original);
    } else {
      results.setSuccess(true);

      if (!this.update) {
        this.weightUpdate(edgeweights, original);
      }
    }
    return results;
  }

  private int[][] findResidualFlow(CreditLinks edgeweights, Node[] nodes, Transaction currentTransaction) {

    // first index: previous hop
    // second index: number of hops along the path it took to reach this node
    int[][] previousHops = new int[nodes.length][2];
    for (int i = 0; i < previousHops.length; i++) {
      // -1 is serving as a marker to indicate that node i has not been analyzed yet
      previousHops[i][0] = -1;
    }
    Queue<Integer> q = new LinkedList<>();
    q.add(currentTransaction.src);

    // the source does not have a previous hop
    previousHops[currentTransaction.src][0] = -2;

    int mes = 0;
    while (!q.isEmpty()) {
      int currentNode = q.poll();

      int[] allOutgoingNeighbors = nodes[currentNode].getOutgoingEdges();
      for (int neighbor : allOutgoingNeighbors) {

        // if the neighbor has not been inspected yet, and has some outgoing credit available
        if (previousHops[neighbor][0] == -1 && edgeweights.getMaxTransactionAmount(currentNode, neighbor) > 0) {

          // set previous hop
          previousHops[neighbor][0] = currentNode;

          // set the number of hops to the amount of hops to get to the previous node plus 1
          previousHops[neighbor][1] = previousHops[currentNode][1] + 1;
          mes++;

          if (neighbor == currentTransaction.dst) {
            // assemble the residual path that reached the dest by going backwards until reaching
            // the source
            int[] residualPath = new int[previousHops[neighbor][1] + 1];
            int backwardsNeighborIter = neighbor;
            while (backwardsNeighborIter != -2) {
              residualPath[previousHops[backwardsNeighborIter][1]] = backwardsNeighborIter;
              backwardsNeighborIter = previousHops[backwardsNeighborIter][0];
            }
            int[] stats = {mes};
            return new int[][]{residualPath, stats};
          }
          q.add(neighbor);
        }
      }
    }
    return new int[][]{new int[]{mes}};
  }

  private void weightUpdate(CreditLinks edgeweights, Map<Edge, Double> updateWeight) {
    for (Entry<Edge, Double> entry : updateWeight.entrySet()) {
      edgeweights.setWeight(entry.getKey(), entry.getValue());
    }
  }

  @Override
  public Single[] getSingles() {
    List<Single> l = new ArrayList<>(20);
    for (String dataKey : distributions.keySet()) {
      l.add(new Single(CREDIT_MAX_FLOW + SINGLE_NAMES.get(dataKey), distributions.get(dataKey).getAverage()));
    }
    Single[] singles = l.toArray(new Single[l.size() + 2]);

    singles[l.size()] = new Single(CREDIT_MAX_FLOW + "_SUCCESS_DIRECT", this.success_first);
    singles[l.size() + 1] = new Single(CREDIT_MAX_FLOW + "_SUCCESS", this.success);

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
        if (parts.length == 6) {
          double[] link = new double[6];
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
}
