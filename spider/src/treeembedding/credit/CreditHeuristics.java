package treeembedding.credit;

import org.tc33.jheatchart.*;

import java.awt.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map.Entry;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Random;
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


public class CreditHeuristics extends Metric {
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

  HashMap<String, ArrayList<ArrayList<Integer>>> ksp;
  // k shortest paths from every source to destination (src_dst is key, value is an arraylist of k arraylists
  // each representing a path)


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

  public CreditHeuristics(String file, String name, double requeueInt,
                          int max, String links, boolean up, double epoch, String dirName) {
    super("CREDIT_HEURISTICS", new Parameter[]{
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
    this.K = 5;

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

  public CreditHeuristics(String file, String name, double requeueInt, int max,
                          boolean up, double epoch, String dirName) {
    this(file, name, requeueInt, max, null, up, epoch, dirName);
  }


  public CreditHeuristics(String file, String name, double requeueInt, int max, double epoch, String dirName) {
    this(file, name, requeueInt, max, null, true, epoch, dirName);
  }

  public CreditHeuristics(String file, String name, double requeueInt, int max,
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
    LinkedList<Transaction> toRetry = new LinkedList<Transaction>();
    int epoch_cur = 0;
    int epochs = 0;
    double cur_succ = 0;
    int cur_count = 0;
    int num_succ = 0;
    double amt_succ = 0;
    double amt_tried = 0;
    int num_tries = 0;
    double cap = 0.0;
    int c = 0;
    double transAmount = 0;
    boolean first = true;
    Random rand = new Random();

    // find the shortestPaths once
    System.out.println("Computing shortest paths");
    setShortestPaths(g, /*recompute*/ true);
    System.out.println("Computed shortest paths");

    CreditLinks edgecapacities = (CreditLinks) g.getProperty("CREDIT_LINKS");
    this.totalCredit = edgecapacities.getTotalCredit();

    int start = 50000;
    int limit = 2000;
    double[][] linkBalances = new double[edgecapacities.getWeights().size()][limit/*this.transactions.size(  )*/];

    // open files to log credit link data
    FileWriter succ_file = null;
    FileWriter imbal_file = null;
    FileWriter succ_amt_file = null;
    HashMap<Edge, FileWriter> fwMap = new HashMap<Edge, FileWriter>();
    try {
      succ_file = new FileWriter("TimeSeries/" + dirName + "/SuccTimeSeries");
      succ_amt_file = new FileWriter("TimeSeries/" + dirName + "/SuccAmtTimeSeries");
      imbal_file = new FileWriter("TimeSeries/" + dirName + "/ImbalTimeSeries");


      for (Entry<Edge, double[]> entry : edgecapacities.getWeights()) {
        Edge e = entry.getKey();
        int src = e.getSrc();
        int dst = e.getDst();
        cap = edgecapacities.getPot(src, dst);
                                /*if (src < dst)
                                 fwMap.put(new Edge(src, dst), new FileWriter("TimeSeries/" + dirName + "/" + 
                                        src + "_" + dst + ".txt"  ));
                                else
                                 fwMap.put(new Edge(dst, src), new FileWriter("TimeSeries/" + dirName + "/" + 
                                        dst + "_" + src + ".txt"  ));*/
        break; // just to get cap for now
      }
    } catch (Exception e) {
      System.out.println("Failed to create file");
    }

    // loop through transactions
    while (c < this.transactions.size() /*|| toRetry.size() > 0*/) {
      Transaction cur = this.transactions.get(c);
      float imbalanced = 0;
      epoch_cur = (int) Math.floor(cur.time / this.epoch);

      if (epoch_cur != epoch_old) {
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
      }

      // solve problem and get paths and execute on those paths
      CreditLinks edgeCapacities = (CreditLinks) g.getProperty("CREDIT_LINKS");

      setShortestPaths(g, /*recompute*/ false);
      double[] splits = computeSplitsForTxn(cur, edgeCapacities);
      int[] results = executeSplitsForTxn(cur, splits, edgeCapacities);

      cur_count++;
      if (results[0] == 0) {
        cur_succ++;
        transAmount += cur.val;
        amt_succ += cur.val;
        num_succ++;
        //System.out.println("successful transaction");
        //System.out.println("Transaction succeeded from " + cur.src + " to " + cur.dst + " with value " + cur.val);
      }

      num_tries++;
      amt_tried += cur.val;

                      
                            
                            /*System.out.println("Perform transaction s="+ cur.src + " d= "+ cur.dst +
                                                    " val= " + cur.val + " time= "+ cur.time);*/

      // write all link data and success and imbalance data - this txn not taken into account
      // results not used yet
      try {
        double bal = 0.0;
        for (Entry<Edge, double[]> entry : edgecapacities.getWeights()) {
          if (!(c >= start && c < start + limit))
            continue;
          Edge e = entry.getKey();
          int src = e.getSrc();
          int dst = e.getDst();
          int linkCount = (src < dst) ? src : dst;

          if (src < dst) {
            bal = edgecapacities.getWeight(src, dst);
            //fwMap.get(new Edge(src, dst)).append(edgecapacities.getWeight(src, dst) + "\n");
          } else {
            bal = edgecapacities.getWeight(dst, src);
            //fwMap.get(new Edge(src, dst)).append(edgecapacities.getWeight(dst, src) + "\n");
          }
          linkBalances[linkCount][c - start] = bal;
          if (Math.abs(bal) / cap > 0.8) {
            //System.out.println("Imbalanced: " + bal);
            imbalanced++;
          }
        }
        succ_file.append(num_succ / (float) num_tries + "\n");
        imbal_file.append(imbalanced / edgecapacities.getWeights().size() + "\n");
        succ_amt_file.append(amt_succ / amt_tried + "\n");

      } catch (Exception e) {
        System.out.println("failed to write edge data to file or succ/imbalance data");
      }


      //re-queue if necessary
      cur.addPath(results[1]);
      cur.addMes(results[2]);
      if (results[0] == -1) {
        cur.incRequeue(cur.time + rand.nextDouble() * this.requeueInt);
        if (cur.requeue < this.maxTries) {
          int st = 0;
          while (st < toRetry.size() && toRetry.get(st).time < cur.time) {
            st++;
          }
          toRetry.add(st, cur);
        } else {
          mesAll = this.inc(mesAll, cur.mes);
          pathAll = this.inc(pathAll, cur.path);
        }
        //System.out.println("Transaction failed from " + cur.src + " to " + cur.dst + " with value " + cur.val);
      }

      //3 update metrics accordingly
      path = this.inc(path, results[1]);
      mes = this.inc(mes, results[2]);
      if (results[0] == 0) {
        trys = this.inc(trys, cur.requeue);
        this.success++;
        if (cur.requeue == 0) {
          this.success_first++;
        }
        mesAll = this.inc(mesAll, cur.mes);
        pathAll = this.inc(pathAll, cur.path);
        pathSucc = this.inc(pathSucc, results[1]);
        mesSucc = this.inc(mesSucc, results[2]);
        if (this.writeSucc) {
          //System.out.println("Success: " + cur.time + " " + cur.val + " " + cur.src + " " + c  ur.dst);
        }
      } else {
        pathFail = this.inc(pathFail, results[1]);
        mesFail = this.inc(mesFail, results[2]);
        //System.out.println("it failed");
      }
      for (int j = 3; j < results.length; j++) {
        int index = 0;
        if (results[j] < 0) {
          index = 1;
        }
        cAllPath[index]++;
        int val = Math.abs(results[j]);
        pathS = this.inc(pathS, val);
        if (index == 0) {
          pathSF = this.inc(pathSF, val);
        } else {
          pathSNF = this.inc(pathSNF, val);
        }
      }

      epoch_old = epoch_cur;
      epochs++;
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
      succ_amt_file.close();
      for (Edge e : fwMap.keySet())
        fwMap.get(e).close();
    } catch (Exception e) {
      System.out.println("Couldn't close file");
    }

    // plot heat map
    //
    // Step 1: Create our heat map chart using our data.
    HeatChart map = new HeatChart(linkBalances);

    //Step 2: Customise the chart.
    map.setTitle("This is my heat chart title");
    map.setXAxisLabel("X Axis");
    map.setYAxisLabel("Y Axis");
    map.setHighValueColour(Color.blue);
    map.setLowValueColour(Color.yellow);

    // Step 3: Output the chart to a file.
    try {
      map.saveToFile(new File("java-link-balances-heuristic." + start + "_" + limit + "NOTABS.png"));
    } catch (Exception e) {
      e.printStackTrace();
      System.out.println("Failed to save heatMap");
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

  // find the edge with the minimum capacity on this particular path from src to sink
  // that represents the available balance on that path
  private double getAvailableBal(ArrayList<Integer> path, CreditLinks edgeCapacities) {
    int curNode = path.get(0);
    double min = Double.POSITIVE_INFINITY;

    for (int i = 1; i < path.size(); i++) {
      int nextNode = path.get(i);
      double thisEdgeCap = edgeCapacities.getPot(curNode, nextNode);
      if (thisEdgeCap < min) {
        min = thisEdgeCap;
      }
      curNode = nextNode;
    }
    //System.out.println("avail balance on " + path + " is " + min);
    return min;
  }

  // compute how a given transaction should be split between its k shortest paths
  // Heuristic: find the path with the highest avialable balance and give it atleast
  // as much as it takes to bring its balance to that of the path with second highest
  // available balance. Then take equally from both until you hit the path with the third
  // highest balance and so on
  private double[] computeSplitsForTxn(Transaction t, CreditLinks edgeCapacities) {
    String srcDestKey = t.src + "_" + t.dst;
    ArrayList<ArrayList<Integer>> setOfPaths = this.ksp.get(srcDestKey);

    class PathAndBalance implements Comparable<PathAndBalance> {
      int pathId;
      double availBal;

      public PathAndBalance(int id, double bal) {
        this.pathId = id;
        this.availBal = bal;
      }

      public String toString() {
        return "(" + this.pathId + ", " + this.availBal + ")";
      }

      public int compareTo(PathAndBalance p) { //written to be a max heap
        if (this.availBal > p.availBal)
          return -1;
        else if (this.availBal == p.availBal)
          return 0;
        else
          return 1;
      }
    }

    // find the avaiable balance on each path and insert into a maxHeap
    // so that you can get the path with the most balance
    PriorityQueue<PathAndBalance> maxBalanceHeap = new PriorityQueue<PathAndBalance>();
    for (int i = 0; i < setOfPaths.size(); i++) {
      ArrayList<Integer> thisPath = setOfPaths.get(i);
      maxBalanceHeap.offer(new PathAndBalance(i, getAvailableBal(thisPath, edgeCapacities)));
    }

    // iterate through the heap, finding the max available balance, assigning that path
    // to atleast send that much - the next available balance (without exhausting the
    // transaction amount). Then make the top two paths track each other and send
    // atleast as much more as the difference to the third available balance and so on
    double remTxnAmount = t.val;
    HashMap<Integer, Double> pathToSplitMap = new HashMap<Integer, Double>();
    double[] splits = new double[setOfPaths.size()];
    while (maxBalanceHeap.size() > 0 && remTxnAmount > 0) {
      PathAndBalance highestBalPath = maxBalanceHeap.poll();

      PathAndBalance nextHighestBalPath = maxBalanceHeap.peek();
      double secondHighestBal = (nextHighestBalPath == null) ? 0 : nextHighestBalPath.availBal;
      //System.out.println("Most balance is" + highestBalPath + "next is" + nextHighestBalPath);

      // go through and add the difference between
      // adjacent paths on the bal heap
      // to all the other higher bal paths first, then to current path
      // so that all paths now track each other in balance
      double valToAdd = highestBalPath.availBal - secondHighestBal;
      double remTxnDivided = remTxnAmount / (pathToSplitMap.size() + 1);
      valToAdd = Math.min(valToAdd, remTxnDivided);

      for (Integer path : pathToSplitMap.keySet()) {
        pathToSplitMap.put(path, pathToSplitMap.get(path) + valToAdd);
      }
      // add this split amount to the path currently with highest balance
      pathToSplitMap.put(highestBalPath.pathId, valToAdd);
      remTxnAmount -= valToAdd * pathToSplitMap.size();
    }


    for (Integer path : pathToSplitMap.keySet()) {
      splits[path.intValue()] = pathToSplitMap.get(path).doubleValue();
    }

    return splits;
  }


  // execute all the computed splits for the given transaction
  private int[] executeSplitsForTxn(Transaction t, double[] splits, CreditLinks edgeCapacities) {
    String srcDestKey = t.src + "_" + t.dst;
    int totalPathLength = 0;
    double totalAmt = 0.0;

    ArrayList<ArrayList<Integer>> setOfPaths = this.ksp.get(srcDestKey);
    int[] res = new int[3 + setOfPaths.size()];
    for (int i = 0; i < setOfPaths.size(); i++) {
      ArrayList<Integer> path = setOfPaths.get(i);
      if (splits != null) {
        executeSingleTxnSplit(path, splits[i], edgeCapacities);
        totalAmt += splits[i];
      }
      totalPathLength += path.size();
      res[i + 3] = path.size();
    }

    if (totalAmt == t.val)
      res[0] = 0;
    else
      res[0] = 1;
    res[1] = totalPathLength;
    res[2] = 0; // TODO: NUMBER OF MESSAGES

    return res;
  }

  // go through the path for this split of the transaction and decrement
  // capacities on all edges from the src to dest
  private void executeSingleTxnSplit(ArrayList<Integer> pathForSplit, double splitValue, CreditLinks edgeCapacities) {
    int curNode = pathForSplit.get(0).intValue();

    //System.out.println("Sending " + splitValue + "on this path" + pathForSplit);

    if (splitValue == 0)
      return;

    for (int i = 1; i < pathForSplit.size(); i++) {
      int nextNode = pathForSplit.get(i).intValue();
      //System.out.println("decrementing balance for edge " + curNode + " to " + nextNode);
      edgeCapacities.setWeight(curNode, nextNode, splitValue); // should update the difference
      curNode = nextNode;
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
    Single m_av = new Single("CREDIT_HEURISTICS_MES_AV", this.transactionMess.getAverage());
    Single m_Re_av = new Single("CREDIT_HEURISTICS_MES_RE_AV", this.transactionMessRe.getAverage());
    Single m_S_av = new Single("CREDIT_HEURISTICS_MES_SUCC_AV", this.transactionMessSucc.getAverage());
    Single m_F_av = new Single("CREDIT_HEURISTICS_MES_FAIL_AV", this.transactionMessFail.getAverage());

    Single p_av = new Single("CREDIT_HEURISTICS_PATH_AV", this.pathL.getAverage());
    Single p_Re_av = new Single("CREDIT_HEURISTICS_PATH_RE_AV", this.pathLRe.getAverage());
    Single p_S_av = new Single("CREDIT_HEURISTICS_PATH_SUCC_AV", this.pathLSucc.getAverage());
    Single p_F_av = new Single("CREDIT_HEURISTICS_PATH_FAIL_AV", this.pathLFail.getAverage());


    Single pP_av = new Single("CREDIT_HEURISTICS_PATH_SINGLE_AV", this.path_single.getAverage());
    Single pPF_av = new Single("CREDIT_HEURISTICS_PATH_SINGLE_FOUND_AV", this.path_singleFound.getAverage());
    Single pPNF_av = new Single("CREDIT_HEURISTICS_PATH_SINGLE_NF_AV", this.path_singleNF.getAverage());


    Single s1 = new Single("CREDIT_HEURISTICS_SUCCESS_DIRECT", this.success_first);
    Single s = new Single("CREDIT_HEURISTICS_SUCCESS", this.success);

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
