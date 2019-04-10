package treeembedding.credit;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Set;
import java.util.Vector;

import gtna.algorithms.shortestPaths.Dijkstra;
import gtna.data.Single;
import gtna.graph.Edge;
import gtna.graph.Graph;
import gtna.graph.Node;
import gtna.io.DataWriter;
import gtna.io.graphWriter.GtnaGraphWriter;
import gtna.metrics.Metric;
import gtna.networks.Network;
import gtna.util.Config;
import gtna.util.Distribution;
import gtna.util.parameter.DoubleParameter;
import gtna.util.parameter.IntParameter;
import gtna.util.parameter.Parameter;
import gurobi.*;

public class CreditOptimal extends Metric {
  //input parameters
  //TODO: vibhaa: do i need all of this
  Vector<Transaction> transactions; //vector of transactions, sorted by time
  double requeueInt; //interval until a failed transaction is re-tried; irrelevant if !dynRepair as
  //retry is start of next epoch
  int maxTries;
  Queue<double[]> newLinks;
  boolean update;
  Vector<Edge> zeroEdges;
  Graph graph;
  double epoch;


  // TODO: other things that might be useful
  // partitioner to split transaction values on multiple links
  double[][] lambda; // traffic matrix - lambda[i][j] avg. txns per epoch from src i to dest j
  int K; // number of paths in k shortest paths
  double L; // cost of going on chain
  double R; // reset cost
  HashMap<String, ArrayList<ArrayList<Integer>>> ksp;
  // k shortest paths from every source to destination (src_dst is key, value is an arraylist of k arraylists each representing a path
  Vector<Transaction> currentTxnSet;

  //computed metrics
  Distribution transactionMess; //distribution of #messages needed for one transaction trial
  //(i.e., each retransaction count as new transactions)
  Distribution transactionMessRe; //distribution of #messages needed, counting re-transactions as part of transaction
  Distribution transactionMessSucc; //messages successful transactions
  Distribution transactionMessFail; //messages failed transactions
  Distribution pathL; //distribution of path length (sum over all trees!)
  Distribution pathLRe; //distribution of path length counting re-transactions as one
  Distribution pathLSucc; //path length successful transactions
  Distribution pathLFail; //path length failed transactions
  Distribution trials; //Distribution of number of trials needed to get through
  Distribution path_single; //distribution of single paths
  Distribution path_singleFound; //distribution of single paths, only discovered paths
  Distribution path_singleNF; //distribution of single paths, not found desti
  Distribution utilizationL; // distribution of utilizations
  double success_first; //fraction of transactions successful in first try
  double success; // fraction of transactions successful at all
  double[] succs;
  double[] utilization; // amount in movement over the total credit in the network
  double totalCredit; //not changed after the beginning

  boolean log = false;
  boolean writeSucc = true;
  String dirName = null;

  public CreditOptimal(String file, String name, double requeueInt,
                       int max, String links, boolean up, double epoch, String dirName) {
    super("CREDIT_OPTIMAL", new Parameter[]{
            new DoubleParameter("REQUEUE_INTERVAL", requeueInt),
            new IntParameter("MAX_TRIES", max)});
    transactions = this.readList(file);
    this.requeueInt = requeueInt;
    this.maxTries = max;
    if (links != null) {
      this.newLinks = this.readLinks(links);
    } else {
      this.newLinks = new LinkedList<double[]>();
    }
    this.update = up;
    this.epoch = epoch;
    this.K = 6;
    this.L = 100;
    this.R = 2;

    this.currentTxnSet = new Vector<Transaction>();

    this.dirName = dirName;
    File f = null;
    try {
      f = new File("TimeSeries/" + dirName);
      f.delete();
      f.mkdir();
    } catch (Exception e) {
      System.out.println("coulnd't create directory");
    }
  }

  public CreditOptimal(String file, String name, double requeueInt, int max,
                       boolean up, double epoch, String dirName) {
    this(file, name, requeueInt, max, null, up, epoch, dirName);
  }


  public CreditOptimal(String file, String name, double requeueInt, int max, double epoch, String dirName) {
    this(file, name, requeueInt, max, null, true, epoch, dirName);
  }

  public CreditOptimal(String file, String name, double requeueInt, int max,
                       String links, double epoch, String dirName) {
    this(file, name, requeueInt, max, links, true, epoch, dirName);
  }


  @Override
  public void computeData(Graph g, Network n, HashMap<String, Metric> m) {
    // seems like most of the logic goes here
    int count = 0;
    long[] trys = new long[2];
    long[] path = new long[2];
    long[] pathAll = new long[2];
    long[] pathSucc = new long[2];
    long[] pathFail = new long[2];
    long[] mes = new long[2];
    long[] mesAll = new long[2];
    long[] mesSucc = new long[2];
    long[] mesFail = new long[2];
    long[] pathS = new long[2];
    long[] pathSF = new long[2];
    long[] pathSNF = new long[2];
    int[] cAllPath = new int[2];
    success_first = 0;
    success = 0;
    Vector<Double> succR = new Vector<Double>();
    Vector<Integer> stabMes = new Vector<Integer>();
    Vector<Double> txnAmounts = new Vector<Double>();
    Node[] nodes = g.getNodes();
    boolean[] exclude = new boolean[nodes.length];

    // pre-compute paths for every pair? to get a choice of paths for k shortest paths?
    // segment transactions into epochs
    // in each epoch
    // find the rate of transactions - lambdas for the last epoch
    // use the preferences from the last epoch to solve the problem
    //
    // update lambda for every transaction that you observe for that pair
    // update preferences based on what path was actually taken and how often in the past
    //


    // go over transactions
    // TODO: do i have a retry list
    int epoch_old = 0;
    int epoch_cur = 0;
    int epochs = 0;
    double cur_succ = 0;
    int cur_count = 0;
    int num_succ = 0;
    int num_tries = 0;
    double cap = 0.0;
    int c = 0;
    double transAmount = 0;
    boolean first = true;
    this.lambda = new double[g.getNodeCount()][g.getNodeCount()];

    // find the shortestPaths once
    System.out.println("Computing shortest paths");
    setShortestPaths(g, /*recompute*/ true);
    System.out.println("Computed shortest paths");

    CreditLinks edgecapacities = (CreditLinks) g.getProperty("CREDIT_LINKS");
    this.totalCredit = edgecapacities.getTotalCredit();

    // open files to log credit link data
    FileWriter succ_file = null;
    FileWriter imbal_file = null;
    HashMap<Edge, FileWriter> fwMap = new HashMap<Edge, FileWriter>();
    try {
      succ_file = new FileWriter("TimeSeries/" + dirName + "/SuccTimeSeries");
      imbal_file = new FileWriter("TimeSeries/" + dirName + "/ImbalTimeSeries");
      for (Entry<Edge, double[]> entry : edgecapacities.getWeights()) {
        Edge e = entry.getKey();
        int src = e.getSrc();
        int dst = e.getDst();
        cap = edgecapacities.getPot(src, dst);
        if (src < dst)
          fwMap.put(new Edge(src, dst), new FileWriter("TimeSeries/" + dirName + "/" + src + "_" + dst + ".txt"));
        else
          fwMap.put(new Edge(dst, src), new FileWriter("TimeSeries/" + dirName + "/" + dst + "_" + src + ".txt"));
      }
    } catch (Exception e) {
      System.out.println("Failed to create file");
    }

    while (c < this.transactions.size() /*|| toRetry.size() > 0*/) {
      Transaction cur = this.transactions.get(c);
      float imbalanced = 0;
      epoch_cur = (int) Math.floor(cur.time / this.epoch);
      if (epoch_cur != epoch_old) {
        if (first == true) {
          first = false;
          epoch_old = epoch_cur;
          continue;
        }
        // aggregate metrics from last epoch and reset
        cur_succ = (cur_count == 0) ? 1 : cur_succ / (double) cur_count;
        succR.add(cur_succ);
        for (int j = epoch_old + 2; j <= epoch_cur; j++) {
          succR.add(1.0);
        }
        txnAmounts.add(transAmount);
        cur_count = 0;
        cur_succ = 0;
        transAmount = 0;

        // solve problem and get paths and execute on those paths
        System.out.println("new epoch to solve");
        CreditLinks edgeCapacities = (CreditLinks) g.getProperty("CREDIT_LINKS");
        double[][][] weightsOnPaths = findPathPreferences(g, edgeCapacities);
        int[][] results = executeEpochTxns(weightsOnPaths, edgeCapacities);

        System.out.println("Perform transaction s=" + cur.src + " d= " + cur.dst +
                " val= " + cur.val + " time= " + cur.time);

        //update metrics for this txn set
        int[] pathIndices = results[0];
        int[] pathLengths = results[1];
        for (int i = 0; i < pathIndices.length; i++) {
          // basically for every txn do this
          int pathIndex = pathIndices[i];
          int pathLength = pathLengths[i];

          path = this.inc(path, pathLength);
          mes = this.inc(mes, 0); // n o messages
          if (pathIndex >= 0 && pathIndex <= this.K) {
            cur_succ++;
            this.success++;
            num_succ++;
            this.success_first++; // no retries
            cAllPath[0]++; // success - unclear what this metric is

            mesAll = this.inc(mesAll, cur.mes);
            pathAll = this.inc(pathAll, cur.path);
            pathSucc = this.inc(pathSucc, pathLength);
            mesSucc = this.inc(mesSucc, 0); // no messages
            System.out.println("Success: " + cur.time + " " + cur.val + " " +
                    cur.src + " " + cur.dst);
          } else {
            pathFail = this.inc(pathFail, pathLength); // means nothing - just a blockchain txn
            mesFail = this.inc(mesFail, 0); // no messages
            cAllPath[1]++; // failure list
          }
          //NOTE: there is a pathSF and pathSNF metric that are
          //irrelvant to me in this context
          cur_count++;
          num_tries++;
        }

        // recompute paths for next epoch
        setShortestPaths(g, /*recompute*/ false);
        updateTrafficMatrix(g, epochs, currentTxnSet);
        currentTxnSet.clear();
        epoch_old = epoch_cur;
        epochs++;

        // write all link data and success and imbalance data
        try {
          double bal = 0.0;
          for (Entry<Edge, double[]> entry : edgecapacities.getWeights()) {
            Edge e = entry.getKey();
            int src = e.getSrc();
            int dst = e.getDst();

            if (src < dst) {
              bal = edgecapacities.getWeight(src, dst);
              fwMap.get(new Edge(src, dst)).append(edgecapacities.getWeight(src, dst) + "\n");
            } else {
              bal = edgecapacities.getWeight(dst, src);
              fwMap.get(new Edge(src, dst)).append(edgecapacities.getWeight(dst, src) + "\n");
            }
            if (Math.abs(bal) / cap > 0.8) {
              //System.out.println("Imabalanced: " + bal);
              imbalanced++;
            }
          }
          succ_file.append(num_succ / (float) num_tries + "\n");
          imbal_file.append(imbalanced / fwMap.keySet().size() + "\n");

        } catch (Exception e) {
          System.out.println("failed to write edge data to file or succ/imbalance data");
        }
      }
      currentTxnSet.add(this.transactions.get(c));
      c++;
    }

    // metric update on per txn basis so that it can be aggregated
    //compute metrics
    this.pathL = new Distribution(path, count);
    this.transactionMess = new Distribution(mes, count);
    this.pathLRe = new Distribution(pathAll, transactions.size());
    this.transactionMessRe = new Distribution(mesAll, transactions.size());
    this.pathLSucc = new Distribution(pathSucc, (int) this.success);
    this.transactionMessSucc = new Distribution(mesSucc, (int) this.success);
    this.pathLFail = new Distribution(pathFail, this.transactions.size() - (int) this.success);
    this.transactionMessFail = new Distribution(mesFail, this.transactions.size() - (int) this.success);
    this.trials = new Distribution(trys, (int) this.success);
    this.path_single = new Distribution(pathS, cAllPath[0] + cAllPath[1]);
    this.path_singleFound = new Distribution(pathSF, cAllPath[0]);
    this.path_singleNF = new Distribution(pathSNF, cAllPath[1]);
    this.success = this.success / (double) transactions.size();
    this.success_first = this.success_first / (double) transactions.size();
    this.graph = g;
    this.succs = new double[succR.size()];
    this.utilization = new double[txnAmounts.size()];
    for (int i = 0; i < this.utilization.length; i++) {
      this.utilization[i] = txnAmounts.get(i) / this.totalCredit;
    }
    for (int i = 0; i < this.succs.length; i++) {
      succs[i] = succR.get(i);
    }

    // close all files
    try {
      succ_file.close();
      imbal_file.close();
      for (Edge e : fwMap.keySet())
        fwMap.get(e).close();
    } catch (Exception e) {
      System.out.println("Couldn't close file");
    }
  }

  // find k shortest paths between src i and dest j in the graph
  // // using Dkjstra's
  // TODO: restructuring so that chepeast path can be found necessary
  private void setShortestPaths(Graph g, boolean recompute) {
    if (recompute) {
      Dijkstra dijkstra = new Dijkstra();
      this.ksp = dijkstra.getKShortestPaths(g, this.K);
    }
  }

  // TODO: fix this/refactorprivat GRBLinExpr getLinearResetRateFunction(

  private double[][][] findPathPreferences(Graph g, CreditLinks edgeCapacities) {
    // formulate ILP
    try {
      GRBEnv env = new GRBEnv("centralized_opt_no_fee.log");
      GRBModel model = new GRBModel(env);
      int numNodes = g.getNodeCount();

      // path preference variables
      GRBVar[][][] pathPreferences = new GRBVar[this.K + 1][numNodes][numNodes];
      for (int i = 0; i < numNodes; i++)
        for (int j = 0; j < numNodes; j++)
          for (int k = 0; k < this.K + 1; k++) {
            pathPreferences[k][i][j] = model.addVar(0.0, 1.0, 1.0,
                    GRB.CONTINUOUS, "w_" + i + j + "(" + k + ")");
          }

      System.out.println("Created variables");


      // Set objectives
      GRBLinExpr objExpr = new GRBLinExpr();
      // Part1: cost of on chain transaction
      for (int i = 0; i < numNodes; i++)
        for (int j = 0; j < numNodes; j++) {
          objExpr.addTerm(this.lambda[i][j] * this.L, pathPreferences[this.K][i][j]);
        }
      System.out.println("Blockchain cost added");

      // Part 2: Reset rate cost
      // 2a: find all edges'rates r(u,v) as a function of lambda and preferences
      HashMap<Edge, GRBLinExpr> txnRatePerEdge = new HashMap<Edge, GRBLinExpr>();
      for (int i = 0; i < numNodes; i++)
        for (int j = 0; j < numNodes; j++) {
          String key = i + "_" + j;
          if (i == j)
            continue;
          ArrayList<ArrayList<Integer>> setOfPaths = this.ksp.get(key);

          for (int k = 0; k < setOfPaths.size(); k++) {
            ArrayList<Integer> thisPath = setOfPaths.get(k);
            if (thisPath == null) // no more valid paths for this source dst pair
              break;
            int indexOnPath = thisPath.size() - 1;
            int curNode = thisPath.get(indexOnPath);

            // start from the last node and go back adding up the rates to get effective
            // rate on all edges from source to last destination
            GRBLinExpr rateInc = new GRBLinExpr();
            while (indexOnPath > 0) {
              indexOnPath--;
              int prevNode = thisPath.get(indexOnPath);
              Edge edge = new Edge(prevNode, curNode);

              // add current expression over to the existing expression
              rateInc.addTerm(this.lambda[i][curNode], pathPreferences[k][i][curNode]);
              GRBLinExpr curRate = txnRatePerEdge.getOrDefault(edge, new GRBLinExpr());
              curRate.add(rateInc);
              txnRatePerEdge.put(edge, curRate);
              curNode = prevNode;
            }
          }
        }
      System.out.println("reset rate effect computed");

      //2b: construct an expression summing reset costs for all edges using above rates
      HashMap<Edge, GRBVar> rateDiffPerEdge = new HashMap<Edge, GRBVar>();
      HashMap<Edge, GRBVar> absRateDiffPerEdge = new HashMap<Edge, GRBVar>();
      for (Edge e : txnRatePerEdge.keySet()) {
        rateDiffPerEdge.put(e, model.addVar
                (-200.0, 200.0, 1.0, GRB.CONTINUOUS, "diff_" + e.getSrc() + e.getDst()));
        absRateDiffPerEdge.put(e, model.addVar
                (0, 400.0, 1.0, GRB.CONTINUOUS, "absDiff_" + e.getSrc() + e.getDst()));

        GRBLinExpr rateBackward = txnRatePerEdge.get(e); // depletes balance
        GRBLinExpr rateForward = txnRatePerEdge.get(new Edge(e.getDst(), e.getSrc())); // restores balance

        // find capacity of link
        double cap_forward = edgeCapacities.getPot(e.getSrc(), e.getDst());
        double cap_backward = edgeCapacities.getPot(e.getDst(), e.getSrc());
        double capacity = cap_forward + cap_backward;

        //approximate function
        rateForward.multAdd(-1, rateBackward);
        model.addConstr(rateForward, GRB.EQUAL, rateDiffPerEdge.get(e), "diff constraint");
        model.addGenConstrAbs(rateDiffPerEdge.get(e), absRateDiffPerEdge.get(e), "absolute value constraint");
        //double resetRate = this.resetRateFunction(rateForward, rateBackward)(rateForward - rateBackward) /
        // (capacity/(1 + rateRatio^(capacity/2.0)) - capacity/2.0);

        objExpr.addTerm(this.R * 2 / capacity, absRateDiffPerEdge.get(e));
      }
      System.out.println("actually added reset rate");


      model.setObjective(objExpr, GRB.MINIMIZE);

      // Addiitonal Constraint: preferences across k paths sum upto 1
      for (int i = 0; i < numNodes; i++)
        for (int j = 0; j < numNodes; j++) {
          GRBLinExpr expr = new GRBLinExpr();
          for (int k = 0; k < this.K + 1; k++) {
            expr.addTerm(1.0, pathPreferences[k][i][j]);
          }
          model.addConstr(expr, GRB.EQUAL, 1.0, "c_" + i + j);
        }

      // optimize the model
      System.out.println("starting solving");
      model.optimize();
      System.out.println("solved");

      // path preference outcomes
      double[][][] weightsOnPaths = new double[this.K + 1][numNodes][numNodes];
      for (int i = 0; i < numNodes; i++)
        for (int j = 0; j < numNodes; j++)
          for (int k = 0; k < this.K + 1; k++) {
            weightsOnPaths[k][i][j] = pathPreferences[k][i][j].get(GRB.DoubleAttr.X);
          }

      // dispose of model
      model.dispose();
      env.dispose();

      return weightsOnPaths;
    } catch (GRBException e) {
      System.out.println("Error Code: " + e.getErrorCode() + ". " + e.getMessage());
    }

    return null;
  }

  // find the edge with the minimum capacity on this particular path from src to sink
  private double getMinEdge(int[] path, int source, int sink, CreditLinks edgeCapacities) {
    int curNode = source;
    double min = Double.POSITIVE_INFINITY;

    for (int i = 1; i < path.length; i++) {
      int nextNode = path[i];
      double thisEdgeCap = edgeCapacities.getPot(curNode, nextNode);
      if (thisEdgeCap < min) {
        min = thisEdgeCap;
      }
      curNode = nextNode;
    }
    return min;
  }

  // sample from the weight preferences for this source dest path and return index of
  // the picked path
  private int sample(double[][][] weightsOnPaths, int src, int dst) {
    Double[] weights = new Double[this.K + 1];
    for (int i = 0; i < this.K + 1; i++) {
      weights[i] = (Double) weightsOnPaths[i][src][dst];
    }
    // copy to retrieve indices later
    ArrayList<Double> originalWeights = new ArrayList<Double>(Arrays.asList(weights));

    // construct cdf
    Arrays.sort(weights);
    HashMap<Double, Integer> weightsToIndex = new HashMap<Double, Integer>();
    double sum = 0.0;
    for (int i = 1; i < this.K + 1; i++) {
      int originalIndex = originalWeights.indexOf(weights[i]);
      originalWeights.remove(weights[i]);
      weights[i] += weights[i - 1];
      weightsToIndex.put(weights[i], originalIndex);
    }

    double randomNumber = Math.random();

    for (int i = 0; i < this.K + 1; i++) {
      if (randomNumber < weights[i]) {
        return weightsToIndex.get(weights[i]);
      }
    }

    // should not be encountered
    throw new UnsupportedOperationException("sampling error");
  }

  // do a block chain transaction
  private void doBlockChainTxn(Transaction t) {
    // TODO: some notion of cost that you keep adding to
    //nothing actually happens rn, no notion of individual balances
    ;
  }


  // execute all the transactions in this path according to the weight preferences
  // on paths from source to destination
  private int[][] executeEpochTxns(double[][][] weightsOnPaths, CreditLinks edgeCapacities) {
    // take particular path with a certain probability proportional to the preferences
    // update the weights on those paths
    ArrayList<Integer> pathIndices = new ArrayList<Integer>(this.currentTxnSet.size());
    ArrayList<Integer> pathLengths = new ArrayList<Integer>(this.currentTxnSet.size());
    for (Transaction t : this.currentTxnSet) {
      // sample from weights to get a path until you get a path with minimum value
      Set<Integer> pathsTried = new HashSet<Integer>();
      double minFlow = Double.NEGATIVE_INFINITY;
      int pathToTry = -1;
      int pathLength = -1;
      int[] path = null;
      while (pathsTried.size() < this.K && minFlow < t.val) {
        pathToTry = sample(weightsOnPaths, t.src, t.dst);
        if (pathToTry == this.K) {
          doBlockChainTxn(t);
          break;
        }
        String srcDstKey = t.src + "_" + t.dst;
        ArrayList<Integer> pathList = this.ksp.get(srcDstKey).get(pathToTry);
        path = pathList.stream().mapToInt(Integer::intValue).toArray();

        minFlow = getMinEdge(path, t.src, t.dst, edgeCapacities);
        pathLength = path.length;
        pathsTried.add(pathToTry);
      }

      // valid path found execute now
      pathIndices.add(pathToTry);
      pathLengths.add(pathLength);
      executeSingleTxnOnPath(path, t, edgeCapacities);
    }
    int[] pathIndicesArr = pathIndices.stream().mapToInt(Integer::intValue).toArray();
    int[] pathLengthsArr = pathLengths.stream().mapToInt(Integer::intValue).toArray();
    System.out.println("Finished executing");
    return new int[][]{pathIndicesArr, pathLengthsArr};
  }

  // go through the path for this transaction and decrement
  // capacities on all edges from the src to dest
  private void executeSingleTxnOnPath(int[] path, Transaction t, CreditLinks edgeCapacities) {
    int curNode = t.src;

    for (int i = 1; i < path.length; i++) {
      int nextNode = path[i];
      //double w = edgeCapcities.getWeight(Edge(curNode, nextNode));
      edgeCapacities.setWeight(new Edge(curNode, nextNode), t.val); // should update the difference
      curNode = nextNode;
    }
  }

  // update the traffic matrix based on the transactions that happend in the last epoch
  private void updateTrafficMatrix(Graph g, int epochs, Vector<Transaction> currentTxnSet) {
    int numNodes = g.getNodes().length;
    double[][] epochTxnCount = new double[numNodes][numNodes];

    for (Transaction t : this.currentTxnSet) {
      epochTxnCount[t.src][t.dst]++;
    }

    for (int i = 0; i < numNodes; i++)
      for (int j = 0; j < numNodes; j++) {
        this.lambda[i][j] = (this.lambda[i][j] * epochs + epochTxnCount[i][j]) / (epochs + 1);
      }
  }

  private void addLink(int src, int dst, double[] weight, Graph g) {
    CreditLinks edgeweights = (CreditLinks) g.getProperty("CREDIT_LINKS");
    edgeweights.setWeight(new Edge(src, dst), weight);
  }

  private void weightUpdate(CreditLinks edgeweights, HashMap<Edge, Double> updateWeight) {
    Iterator<Entry<Edge, Double>> it = updateWeight.entrySet().iterator();
    while (it.hasNext()) {
      Entry<Edge, Double> entry = it.next();
      edgeweights.setWeight(entry.getKey(), entry.getValue());
    }
  }

  @Override
  public Single[] getSingles() {
    Single m_av = new Single("CREDIT_OPTIMAL_MES_AV", this.transactionMess.getAverage());
    Single m_Re_av = new Single("CREDIT_OPTIMAL_MES_RE_AV", this.transactionMessRe.getAverage());
    Single m_S_av = new Single("CREDIT_OPTIMAL_MES_SUCC_AV", this.transactionMessSucc.getAverage());
    Single m_F_av = new Single("CREDIT_OPTIMAL_MES_FAIL_AV", this.transactionMessFail.getAverage());

    Single p_av = new Single("CREDIT_OPTIMAL_PATH_AV", this.pathL.getAverage());
    Single p_Re_av = new Single("CREDIT_OPTIMAL_PATH_RE_AV", this.pathLRe.getAverage());
    Single p_S_av = new Single("CREDIT_OPTIMAL_PATH_SUCC_AV", this.pathLSucc.getAverage());
    Single p_F_av = new Single("CREDIT_OPTIMAL_PATH_FAIL_AV", this.pathLFail.getAverage());


    Single pP_av = new Single("CREDIT_OPTIMAL_PATH_SINGLE_AV", this.path_single.getAverage());
    Single pPF_av = new Single("CREDIT_OPTIMAL_PATH_SINGLE_FOUND_AV", this.path_singleFound.getAverage());
    Single pPNF_av = new Single("CREDIT_OPTIMAL_PATH_SINGLE_NF_AV", this.path_singleNF.getAverage());


    Single s1 = new Single("CREDIT_OPTIMAL_SUCCESS_DIRECT", this.success_first);
    Single s = new Single("CREDIT_OPTIMAL_SUCCESS", this.success);

    double utilSum = 0;
    for (double u : utilization) {
      utilSum += u;
    }
    Single utilization_av = new Single("UTILIZATION", utilSum / utilization.length);

    return new Single[]{m_av, m_Re_av, m_S_av, m_F_av, p_av, p_Re_av, p_S_av, p_F_av,
            s1, s, pP_av, pPF_av, pPNF_av, utilization_av};
  }

  @Override
  public boolean applicable(Graph g, Network n, HashMap<String, Metric> m) {
    return g.hasProperty("CREDIT_LINKS");
  }

  private LinkedList<double[]> readLinks(String file) {
    LinkedList<double[]> vec = new LinkedList<double[]>();
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


  private long[] inc(long[] values, int index) {
    try {
      values[index]++;
      return values;
    } catch (ArrayIndexOutOfBoundsException e) {
      long[] valuesNew = new long[index + 1];
      System.arraycopy(values, 0, valuesNew, 0, values.length);
      valuesNew[index] = 1;
      return valuesNew;
    }
  }

  @Override
  public boolean writeData(String folder) {
    boolean succ = true;
    succ &= DataWriter.writeWithIndex(this.transactionMess.getDistribution(),
            this.key + "_MESSAGES", folder);
    succ &= DataWriter.writeWithIndex(this.transactionMessRe.getDistribution(),
            this.key + "_MESSAGES_RE", folder);
    succ &= DataWriter.writeWithIndex(this.transactionMessSucc.getDistribution(),
            this.key + "_MESSAGES_SUCC", folder);
    succ &= DataWriter.writeWithIndex(this.transactionMessFail.getDistribution(),
            this.key + "_MESSAGES_FAIL", folder);

    succ &= DataWriter.writeWithIndex(this.pathL.getDistribution(),
            this.key + "_PATH_LENGTH", folder);
    succ &= DataWriter.writeWithIndex(this.pathLRe.getDistribution(),
            this.key + "_PATH_LENGTH_RE", folder);
    succ &= DataWriter.writeWithIndex(this.pathLSucc.getDistribution(),
            this.key + "_PATH_LENGTH_SUCC", folder);
    succ &= DataWriter.writeWithIndex(this.pathLFail.getDistribution(),
            this.key + "_PATH_LENGTH_FAIL", folder);

    succ &= DataWriter.writeWithIndex(this.trials.getDistribution(),
            this.key + "_TRIALS", folder);


    succ &= DataWriter.writeWithIndex(this.path_single.getDistribution(),
            this.key + "_PATH_SINGLE", folder);
    succ &= DataWriter.writeWithIndex(this.path_singleFound.getDistribution(),
            this.key + "_PATH_SINGLE_FOUND", folder);
    succ &= DataWriter.writeWithIndex(this.path_singleNF.getDistribution(),
            this.key + "_PATH_SINGLE_NF", folder);
    succ &= DataWriter.writeWithIndex(this.succs,
            this.key + "_SUCC_RATIOS", folder);
    succ &= DataWriter.writeWithIndex(this.utilization,
            this.key + "_UTILIZATION", folder);


    if (Config.getBoolean("SERIES_GRAPH_WRITE")) {
      (new GtnaGraphWriter()).writeWithProperties(graph, folder + "graph.txt");
    }


    return succ;
  }

  private Vector<Transaction> readList(String file) {
    Vector<Transaction> vec = new Vector<Transaction>();
    try {
      BufferedReader br = new BufferedReader(new FileReader(file));
      String line;
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
          Transaction ta = new Transaction(0,
                  Double.parseDouble(parts[0]),
                  Integer.parseInt(parts[1]),
                  Integer.parseInt(parts[2]));
          vec.add(ta);
        }
      }
      br.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
    return vec;
  }


}
