package treeembedding.credit;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Random;
import java.util.TreeSet;
import java.util.Vector;

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


public class CreditMaxFlowPacket extends Metric {
  //input parameters
  Vector<Transaction> transactions; //vector of transactions, sorted by time
  double requeueInt; //interval until a failed transaction is re-tried; irrelevant if !dynRepair as
  //retry is start of next epoch
  int maxTries;
  Queue<double[]> newLinks;
  boolean update;
  Vector<Edge> zeroEdges;
  Graph graph;
  double epoch;


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
  Distribution path_singleNF; //distribution of single paths, not found dest
  Distribution utilizationL; // distribution of utilizations
  double success_first; //fraction of transactions successful in first try
  double success; // fraction of transactions successful at all
  double[] succs;
  double[] utilization; //amount in movement/total credit in the network
  double totalCredit; // not changed after the beginning

  boolean log = false;
  boolean writeSucc = true;
  String dirName = null;

  // forced to make these instance variables to factor code
  //
  double cur_succ = 0;
  int num_succ = 0;
  double amt_succ = 0;
  double amt_tried = 0;
  int num_tries = 0;
  double cap = 0.0;
  int cur_count = 0;
  double transAmount = 0;

  // filewriters
  FileWriter succ_file = null;
  FileWriter succ_amt_file = null;
  FileWriter imbal_file = null;

  //metrics
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

  // ALL INFLIGHT TRANSACTIONS
  TreeSet<Transaction> inflightTxns = new TreeSet<Transaction>(TransactionComparators.RemValueComparator);


  public CreditMaxFlowPacket(String file, String name, double requeueInt,
                             int max, String links, boolean up, double epoch, String dirName) {
    super("CREDIT_MAX_FLOW_PACKET", new Parameter[]{
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

  public CreditMaxFlowPacket(String file, String name, double requeueInt, int max,
                             boolean up, double epoch, String dirName) {
    this(file, name, requeueInt, max, null, up, epoch, dirName);
  }


  public CreditMaxFlowPacket(String file, String name, double requeueInt, int max, double epoch, String dirName) {
    this(file, name, requeueInt, max, null, true, epoch, dirName);
  }

  public CreditMaxFlowPacket(String file, String name, double requeueInt, int max,
                             String links, double epoch, String dirName) {
    this(file, name, requeueInt, max, links, true, epoch, dirName);
  }


  // check if any inflight txns can progress
  public boolean checkTxnsForProgress(Graph g, Node[] nodes, boolean[] exclude) {
    System.out.println("checking progress amongst " + this.inflightTxns.size());
    boolean progressed = false;
    for (Transaction t : this.inflightTxns) {
      // if you can complete one transaction, stop
      double originalVal = t.val;
      int[] attempt = this.fordFulkerson(t, g, nodes, exclude);
      if (attempt[0] == 0) {
        //succeeded
        updateMetrics(t, originalVal, attempt, false, false);
        progressed = true;
      }
    }
    return progressed;
  }

  @Override
  public void computeData(Graph g, Network n, HashMap<String, Metric> m) {


    int count = 0;

    success_first = 0;
    success = 0;
    Vector<Double> succR = new Vector<Double>();
    Vector<Integer> stabMes = new Vector<Integer>();
    Vector<Double> txnAmounts = new Vector<Double>();
    Node[] nodes = g.getNodes();
    boolean[] exclude = new boolean[nodes.length];

    //go over transactions
    LinkedList<Transaction> toRetry = new LinkedList<Transaction>();
    int epoch_old = 0;
    int epoch_cur = 0;
    cur_succ = 0;
    num_succ = 0;
    amt_succ = 0;
    amt_tried = 0;
    num_tries = 0;
    cap = 0.0;
    cur_count = 0;
    int c = 0;
    transAmount = 0;
    Random rand = new Random();
    CreditLinks edgecapacities = (CreditLinks) g.getProperty("CREDIT_LINKS");
    this.totalCredit = edgecapacities.getTotalCredit();

    // open files to log credit link data
    succ_file = null;
    succ_amt_file = null;
    imbal_file = null;
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
				 fwMap.put(new Edge(src, dst), new FileWriter("TimeSeries/" + dirName + "/" + src + "_" + dst + ".txt"));
				else
				 fwMap.put(new Edge(dst, src), new FileWriter("TimeSeries/" + dirName + "/" + dst + "_" + src + ".txt"));*/
        break; // just to get cap for now
      }
    } catch (Exception e) {
      System.out.println("Failed to create file");
    }

    while (c < this.transactions.size() || inflightTxns.size() > 0) {
      //0: decide which is next transaction: previous one or new one? and add new links if any
      Transaction a = c < this.transactions.size() ? this.transactions.get(c) : null;
      Transaction b = inflightTxns.size() > 0 ? inflightTxns.first() : null;
      Transaction cur = null;

      float imbalanced = 0;
      boolean firstTry = false;

      // write all link data
      try {
        double bal = 0.0;
        for (Entry<Edge, double[]> entry : edgecapacities.getWeights()) {
          Edge e = entry.getKey();
          int src = e.getSrc();
          int dst = e.getDst();

          cap = edgecapacities.getPot(src, dst);
          if (src < dst) {
            bal = edgecapacities.getWeight(src, dst);
            //fwMap.get(new Edge(src, dst)).append(edgecapacities.getWeight(src, dst) + "\n");
          } else {
            bal = edgecapacities.getWeight(dst, src);
            //fwMap.get(new Edge(src, dst)).append(edgecapacities.getWeight(dst, src) + "\n");
          }
          if (Math.abs(bal) / cap > 0.8) {
            //System.out.println("Imabalanced: " + bal);
            imbalanced++;
          }
        }
      } catch (Exception e) {
        System.out.println("failed to write edge data to file");
      }

      if (a != null/* && (b == null || a.time < b.time)*/) {
        cur = a;
        c++;
        firstTry = true;
      } else {
        cur = inflightTxns.pollFirst();
        firstTry = false;
      }
      if (!this.newLinks.isEmpty()) {
        double nt = this.newLinks.peek()[0];
        while (nt <= cur.time) {
          double[] link = this.newLinks.poll();
          this.addLink((int) link[1], (int) link[2], new double[]{link[3], link[4], link[5]}, g);
          nt = this.newLinks.isEmpty() ? Double.MAX_VALUE : this.newLinks.peek()[0];
        }
        System.out.println("adding new links");
      }

      count++;
      //if (log){
					/*System.out.println("Perform transaction s="+ cur.src + " d= "+ cur.dst + 
							" val= " + cur.val + " time= "+ cur.time);*/
      //}

      epoch_cur = (int) Math.floor(cur.time / epoch);
      if (epoch_cur != epoch_old) {
        cur_succ = cur_count == 0 ? 1 : cur_succ / (double) cur_count;
        succR.add(cur_succ);
        for (int j = epoch_old + 2; j <= epoch_cur; j++) {
          succR.add(1.0);
        }
        txnAmounts.add(transAmount);
        cur_count = 0;
        cur_succ = 0;
        transAmount = 0;
        System.out.println("epoch completion");
      }

      //2: execute the transaction
      double originalVal = cur.val;
      int[] results = this.fordFulkerson(cur, g, nodes, exclude);
      try {
        imbal_file.append(imbalanced / edgecapacities.getWeights().size() + "\n");
      } catch (Exception e) {
        System.out.println("Unable to write imbal data");
      }

      updateMetrics(cur, originalVal, results, false, firstTry);
      boolean progressed = checkTxnsForProgress(g, nodes, exclude);
      if (!progressed && c >= transactions.size()) {
        // no more progress can be made
        // record these failures somehow TODO
        System.out.println("NOTHIGN IS PROGRESSING");
        break;
      }

      epoch_old = epoch_cur;
    }

    updateMetrics(count, succR, txnAmounts); // all metrics are done
    this.graph = g;


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


  }

  private void updateMetrics(Transaction cur, double originalVal, int[] results, boolean done, boolean firstTry) {
    updateMetrics(cur, originalVal, results, done, firstTry, 0, null, null);
  }

  private void updateMetrics(int count, Vector<Double> succR, Vector<Double> txnAmounts) {
    updateMetrics(null, 0, null, true /*done*/, false /*firstTry*/, count, succR, txnAmounts);
  }


  private void updateMetrics(Transaction cur, double originalVal, int[] results,
                             boolean done, boolean firstTry, int count, Vector<Double> succR, Vector<Double> txnAmounts) {

    if (done) {
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
      this.succs = new double[succR.size()];
      this.utilization = new double[txnAmounts.size()];
      for (int i = 0; i < this.utilization.length; i++) {
        this.utilization[i] = txnAmounts.get(i) / this.totalCredit;
      }
      for (int i = 0; i < this.succs.length; i++) {
        succs[i] = succR.get(i);
      }
    } else {
      // first set of metrics
      cur_count++;
      if (results[0] == 0) {
        cur_succ++;
        transAmount += originalVal - cur.val; // used to compute utilization or amt moving around
        amt_succ += originalVal - cur.val; // used to compute amount succeeded
        if (cur.txnComplete) {
          num_succ++;
        }

        //System.out.println("successful transaction");
      }

      if (firstTry) {
        num_tries++;
        amt_tried += originalVal;


        try {
          succ_file.append(num_succ / (float) num_tries + "\n");
          succ_amt_file.append(amt_succ / amt_tried + "\n");
        } catch (Exception e) {
          System.out.println("couldn't write succ or imbal data");
        }
      }

      //re-queue if necessary
      cur.addPath(results[1]);
      //System.out.println("path is" + results[1]);
      cur.addMes(results[2]);
      if (results[0] == -1) {
        //System.out.println("No change could be affected for Transaction from " + cur.src + " to " + cur.dst + " with value " + cur.val + " on the firsttry" + firstTry);
                                    /*cur.incRequeue(cur.time+rand.nextDouble()*this.requeueInt);
                                    if (cur.requeue < this.maxTries){
                                            int st = 0;
                                            while (st < toRetry.size() && toRetry.get(st).time < cur.time){
                                                    st++;
                                            }
                                     toRetry.add(st, cur);
                                    } else {*/
        mesAll = this.inc(mesAll, cur.mes);
        pathAll = this.inc(pathAll, cur.path);
        //}
      }

      //3 update metrics accordingly
      path = this.inc(path, results[1]);
      mes = this.inc(mes, results[2]);
      if (results[0] == 0) {
        trys = this.inc(trys, cur.requeue);
        if (cur.txnComplete) {
          this.success++;
          if (cur.requeue == 0) {
            this.success_first++;
          }
        }
        mesAll = this.inc(mesAll, cur.mes);
        pathAll = this.inc(pathAll, cur.path);
        pathSucc = this.inc(pathSucc, results[1]);
        mesSucc = this.inc(mesSucc, results[2]);
        if (this.writeSucc) {
          //System.out.println("Success: " + cur.time + " " + cur.val + " " + cur.src + " " + cur.dst);
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
    }
  }

  private int[] fordFulkerson(Transaction cur, Graph g, Node[] nodes, boolean[] exclude) {
    CreditLinks edgeweights = (CreditLinks) g.getProperty("CREDIT_LINKS");
    HashMap<Edge, Double> original = new HashMap<Edge, Double>();
    int src = cur.src;
    int dest = cur.dst;
    int mes = 0;
    int path = 0;
    Vector<Integer> paths = new Vector<Integer>();

    double totalflow = 0;
    int[][] resp = new int[0][0];
    while (totalflow < cur.val && (resp = findResidualFlow(edgeweights, g.getNodes(), src, dest)).length > 1) {
      if (log) System.out.println("Found residual flow " + resp[0].length);
      //pot flow along this path
      double min = Double.MAX_VALUE;
      Edge minEdge = new Edge(src, dest);
      int[] respath = resp[0];
      //System.out.println("path for this flow is" + Arrays.toString(respath));
      for (int i = 0; i < respath.length - 1; i++) {
        double a = edgeweights.getPot(respath[i], respath[i + 1]);
        if (a < min) {
          min = a;
          minEdge.setSrc(respath[i]);
          minEdge.setDst(respath[i + 1]);
        }
      }
      //update flows

      min = Math.min(min, cur.val - totalflow);

      totalflow = totalflow + min;
      //System.out.println("So far hit flow " + totalflow + " out of " + cur.val);
      for (int i = 0; i < respath.length - 1; i++) {
        int n1 = respath[i];
        int n2 = respath[i + 1];
        double w = edgeweights.getWeight(n1, n2);
        Edge e = n1 < n2 ? new Edge(n1, n2) : new Edge(n2, n1);
        if (!original.containsKey(e)) {
          original.put(e, w);
        }
        if (n1 < n2) {
          edgeweights.setWeight(e, w + min);
          if (log) System.out.println("Set weight of (" + n1 + "," + n2 + ") to " + (w + min));
        } else {
          edgeweights.setWeight(e, w - min);
          if (log) System.out.println("Set weight of (" + n2 + "," + n1 + ") to " + (w - min));
        }
      }
      mes = mes + resp[1][0];
      path = path + respath.length - 1;
      paths.add(respath.length - 1);
    }

    int[] res = new int[3 + paths.size()];
    res[1] = path;
    res[2] = mes;
    for (int j = 3; j < res.length; j++) {
      res[j] = paths.get(j - 3);
    }

    if (totalflow == 0) {
      res[0] = -1;
      if (cur.val == 0.0)
        return res;

      // if this is the only one left, don't reque
      if (!inflightTxns.contains(cur) && inflightTxns.size() > 0)
        inflightTxns.add(cur);
      // no progress was made
    } else if (cur.val - totalflow > 0) {
      // no failure, enqueue into list on inflight trnsactions with a new time, and value
      res[0] = 0;
      cur.incRequeue(0);
      inflightTxns.remove(cur);
      cur.updateVal(cur.val - totalflow);
      inflightTxns.add(cur);
      res[2] = res[2] + resp[0][0];
    } else {
      inflightTxns.remove(cur);
      cur.setTxnComplete();
      //System.out.println("successful txn " + cur);
      res[0] = 0;
      if (!this.update) {
        this.weightUpdate(edgeweights, original);
      }
    }

    return res;
  }

  private int[][] findResidualFlow(CreditLinks ew, Node[] nodes, int src, int dst) {
    int[][] pre = new int[nodes.length][2];
    for (int i = 0; i < pre.length; i++) {
      pre[i][0] = -1;
    }
    Queue<Integer> q = new LinkedList<Integer>();
    q.add(src);
    pre[src][0] = -2;
    boolean found = false;
    int mes = 0;
    while (!found && !q.isEmpty()) {
      int n1 = q.poll();
      int[] out = nodes[n1].getOutgoingEdges();
      for (int n : out) {
        if (pre[n][0] == -1 && ew.getPot(n1, n) > 0.00000001) {
          pre[n][0] = n1;
          pre[n][1] = pre[n1][1] + 1;
          mes++;
          if (n == dst) {
            int[] respath = new int[pre[n][1] + 1];
            while (n != -2) {
              respath[pre[n][1]] = n;
              n = pre[n][0];
            }
            int[] stats = {mes};
            //System.out.println("found a path");
            return new int[][]{respath, stats};
          }
          q.add(n);
        }
                                        /*if (ew.getPot(n1, n) == 0)
                                            System.out.println("Edge saturated from " + n1 + " to " + n);*/

      }
    }
    //System.out.println("No path found");
    return new int[][]{new int[]{mes}};
  }

  /**
   * reconnect disconnected branch with root subroot
   */

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

  @Override
  public Single[] getSingles() {
    Single m_av = new Single("CREDIT_MAX_FLOW_PACKET_MES_AV", this.transactionMess.getAverage());
    Single m_Re_av = new Single("CREDIT_MAX_FLOW_PACKET_MES_RE_AV", this.transactionMessRe.getAverage());
    Single m_S_av = new Single("CREDIT_MAX_FLOW_PACKET_MES_SUCC_AV", this.transactionMessSucc.getAverage());
    Single m_F_av = new Single("CREDIT_MAX_FLOW_PACKET_MES_FAIL_AV", this.transactionMessFail.getAverage());

    Single p_av = new Single("CREDIT_MAX_FLOW_PACKET_PATH_AV", this.pathL.getAverage());
    Single p_Re_av = new Single("CREDIT_MAX_FLOW_PACKET_PATH_RE_AV", this.pathLRe.getAverage());
    Single p_S_av = new Single("CREDIT_MAX_FLOW_PACKET_PATH_SUCC_AV", this.pathLSucc.getAverage());
    Single p_F_av = new Single("CREDIT_MAX_FLOW_PACKET_PATH_FAIL_AV", this.pathLFail.getAverage());


    Single pP_av = new Single("CREDIT_MAX_FLOW_PACKET_PATH_SINGLE_AV", this.path_single.getAverage());
    Single pPF_av = new Single("CREDIT_MAX_FLOW_PACKET_PATH_SINGLE_FOUND_AV", this.path_singleFound.getAverage());
    Single pPNF_av = new Single("CREDIT_MAX_FLOW_PACKET_PATH_SINGLE_NF_AV", this.path_singleNF.getAverage());


    Single s1 = new Single("CREDIT_MAX_FLOW_PACKET_SUCCESS_DIRECT", this.success_first);
    Single s = new Single("CREDIT_MAX_FLOW_PACKET_SUCCESS", this.success);

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


}
