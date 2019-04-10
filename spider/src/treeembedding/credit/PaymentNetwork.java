package treeembedding.credit;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map.Entry;
import java.util.PriorityQueue;
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
import gtna.transformation.spanningtree.MultipleSpanningTree;
import gtna.util.Config;
import gtna.util.Distribution;
import gtna.util.parameter.DoubleParameter;
import gtna.util.parameter.IntParameter;
import gtna.util.parameter.Parameter;
import treeembedding.vouteoverlay.Treeembedding;


public class PaymentNetwork extends Metric {
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
  boolean isPacket = false;
  double deadline;
  double curTime = 0; // in seconds
  int[] roots = new int[]{13, 27}; // HARDCODED based on degree
  double txnDelay = 0.500; // in seconds
  final double epsilon = 1e-10;

  // filewriters
  FileWriter succ_file = null;
  FileWriter succ_amt_file = null;
  FileWriter imbal_file = null;
  FileWriter fail_file = null;
  FileWriter txn_outcome_file = null;
  HashMap<Edge, FileWriter> fwMap = new HashMap<Edge, FileWriter>();

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
  double[][] linkBalances = null;
  String txnFileName = null;

  int updateInterval = 250; //seconds
  double BUFFER_TIME = 0.5;

  HashMap<String, Double> demandMatrix = new HashMap<String, Double>();

  // ALL INFLIGHT TRANSACTIONS
  TreeSet<Transaction> inflightTxns = new TreeSet<Transaction>(TransactionComparators.RemValueComparator);

  TreeSet<Transaction> newInflightTxns = new TreeSet<Transaction>(TransactionComparators.RemValueComparator);

  RoutingAlgorithmTypes routingAlg;

  PriorityQueue<RoutingEvent> eventQueue = new PriorityQueue<RoutingEvent>();

  String timeSeriesPath = "/home/ubuntu/efs/TimeSeries/";
  boolean optimization = true;

  public PaymentNetwork(String file, String name, double requeueInt,
                        int max, String links, boolean up, double epoch, String dirName,
                        RoutingAlgorithmTypes alg, boolean isPacket, double deadline,
                        double txnDelay, boolean optimization) {
    super("PAYMENT_NETWORK", new Parameter[]{
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
    this.optimization = optimization;
    this.update = up;
    this.epoch = epoch;
    this.dirName = dirName;
    this.routingAlg = alg;
    this.isPacket = isPacket;
    this.deadline = (deadline == -1) ? 5.0 : deadline;
    this.txnDelay = (txnDelay == -1) ? 0.5 : txnDelay;
    File temp = new File(file);
    this.txnFileName = temp.getName();
    System.out.println(this.txnFileName);

    if (txnFileName.contains("Ripple"))
      roots = new int[]{8, 118, 120};

    File f = null;
    try {
      f = new File(timeSeriesPath + dirName);
      f.delete();
      f.mkdir();
    } catch (Exception e) {
      System.out.println("coulnd't create directory");
    }

  }

  public PaymentNetwork(String file, String name, double requeueInt, int max,
                        boolean up, double epoch, String dirName, RoutingAlgorithmTypes alg, boolean isPacket) {
    this(file, name, requeueInt, max, null, up, epoch, dirName, alg, isPacket, -1, -1, false);
  }


  public PaymentNetwork(String file, String name, double requeueInt, int max, double epoch,
                        String dirName, RoutingAlgorithmTypes alg, boolean isPacket) {
    this(file, name, requeueInt, max, null, true, epoch, dirName, alg, isPacket, -1,
            -1, false);
  }

  public PaymentNetwork(String file, String name, double requeueInt, int max,
                        String links, double epoch, String dirName, RoutingAlgorithmTypes alg, boolean isPacket, double deadline, double txnDelay, boolean optimize) {
    this(file, name, requeueInt, max, links, true, epoch, dirName, alg, isPacket,
            deadline, txnDelay, optimize);
  }

  // opens all the time series files
  public void openTimeSeriesFiles(CreditLinks edgecapacities) {
    succ_file = null;
    succ_amt_file = null;
    imbal_file = null;
    fail_file = null;
    txn_outcome_file = null;
    try {
      System.out.println("Creating file " + timeSeriesPath + dirName + "/SuccTimeSeries");
      succ_file = new FileWriter(timeSeriesPath + dirName + "/SuccTimeSeries");
      succ_amt_file = new FileWriter(timeSeriesPath + dirName + "/SuccAmtTimeSeries");
      imbal_file = new FileWriter(timeSeriesPath + dirName + "/ImbalTimeSeries");
      fail_file = new FileWriter(timeSeriesPath + dirName + "/FailedTxns");
      txn_outcome_file = new FileWriter(timeSeriesPath + dirName + "/TxnOutcome");


      for (Entry<Edge, double[]> entry : edgecapacities.getWeights()) {
        Edge e = entry.getKey();
        int src = e.getSrc();
        int dst = e.getDst();
        cap = edgecapacities.getPot(src, dst);

        if (!txnFileName.contains("Ripple")) {
          if (src < dst)
            fwMap.put(new Edge(src, dst), new FileWriter(timeSeriesPath +
                    dirName + "/" + src + "_" + dst + ".txt"));
          else
            fwMap.put(new Edge(dst, src), new FileWriter(timeSeriesPath +
                    dirName + "/" + dst + "_" + src + ".txt"));
        } else {
          cap = 10000000;
          break;
        }
      }
    } catch (Exception e) {
      System.out.println("Failed to create file");
    }

  }

  // write all the time series data
  public void writeImbalanceData(CreditLinks edgecapacities, int c, int start, int limit) {
    float imbalanced = 0;
    // write all imbalance data
    try {
      double bal = 0.0;
      for (Entry<Edge, double[]> entry : edgecapacities.getWeights()) {
        Edge e = entry.getKey();
        int src = e.getSrc();
        int dst = e.getDst();
        int linkCount = (src < dst) ? src : dst;

        cap = edgecapacities.getPot(src, dst);
        if (src < dst) {
          bal = edgecapacities.getWeight(src, dst);
          if (c % 2000 == 0 && !this.txnFileName.contains("Ripple"))
            fwMap.get(new Edge(src, dst)).append(edgecapacities.getWeight(src,
                    dst) + "\n");
        } else {
          bal = edgecapacities.getWeight(dst, src);
          if (c % 2000 == 0 && !this.txnFileName.contains("Ripple"))
            fwMap.get(new Edge(dst, src)).append(edgecapacities.getWeight(dst,
                    src) + "\n");

        }
        if (c >= start && c < start + limit)
          linkBalances[linkCount][c - start] = bal;
        if (Math.abs(bal) / cap > 0.8) {
          //System.out.println("Imabalanced: " + bal);
          imbalanced++;
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
      System.out.println("failed to write edge data to file");
    }

    try {
      imbal_file.append(imbalanced / edgecapacities.getWeights().size() + "\n");
    } catch (Exception e) {
      System.out.println("Unable to write imbal data");
    }

  }

  // handle Routing Event
  public void handleEvent(RoutingEvent event, Graph g, boolean[] exclude) {
    curTime = event.getTime();
    switch (event.getType()) {
      case TXN_ARRIVAL:
        handleArrival(event.getTxn(), g, exclude);
        break;
      case TXN_COMPLETION:
        //System.out.println("Handling completion event");
        handleCompletion(event.getTxn(), g, exclude);
        break;
      case DEMAND_UPDATE:
        if (this.routingAlg == RoutingAlgorithmTypes.BALANCEAWARE)
          handleDemandUpdate();
        break;
    }

  }

  // handle TxnArrival
  public void handleArrival(Transaction cur, Graph g, boolean[] exclude) {
    boolean firstTry = true; // arrival corresponds to first arrival
    //System.out.println("Processing an arrival of " + cur + " at time " + curTime);

    // update Demand matrix
    String srcDestKey = cur.src + "_" + cur.dst;
    double value = cur.val;
    if (demandMatrix.containsKey(srcDestKey)) {
      value += demandMatrix.get(srcDestKey);
    }
    demandMatrix.put(srcDestKey, value);


    // route the txn
    double originalVal = cur.val;
    double completedAmt = this.routingAlg.route(cur, g, exclude, this.isPacket, curTime);
    if (log && completedAmt > 0) System.out.println("Completed " + completedAmt + " for " + cur);

    // queue a completion event to release inflight funds
    if ((isPacket && completedAmt > epsilon) || (completedAmt == cur.val) ||
            (completedAmt <= cur.val + epsilon && completedAmt >= cur.val - epsilon)) {
      if (log) System.out.println("completed partially");
      eventQueue.offer(new RoutingEvent(curTime + this.txnDelay,
              RoutingEventType.TXN_COMPLETION, new Transaction(cur)));
    }

    // update metrics
    updateMetrics(cur, originalVal, completedAmt, false, firstTry);
  }

  // handle txn completion
  public void handleCompletion(Transaction cur, Graph g, boolean[] exclude) {
    //txn amount release
    CreditLinks edgeweights = (CreditLinks) g.getProperty("CREDIT_LINKS");
    //System.out.println("Processing the completion of " + cur + "at time " + curTime);
    boolean success = this.routingAlg.releaseInflightFundsForTxn(cur, edgeweights);
    if (log && success) System.out.println("Released funds for " + cur);

    // check if partially complete txns can make progress
    if (isPacket)
      checkTxnsForProgress(g, exclude);
  }


  // handle an update event to the demand matrix
  public void handleDemandUpdate() {
    int intervalNum = (int) (curTime / updateInterval);
    String filename = "/home/ubuntu/lightning_routing/speedy/src/demandMatrix" + intervalNum
            + this.txnFileName;
    try {
      FileWriter demandFile = new FileWriter(filename, false);
      // write demand out to a new file to then be used by optimal
      for (String s : demandMatrix.keySet()) {
        String[] srcDest = s.split("_");
        // demand matrix takes flow per second
        demandFile.append("0 " + demandMatrix.get(s) / updateInterval + " " +
                srcDest[0] + " " + srcDest[1] + "\n");
      }

      // no need to reset, only looking at outstanding demand
      demandFile.close();
    } catch (Exception e) {
      e.printStackTrace();
    }

    routingAlg.initialSetup(true, intervalNum);
  }

  // check if any inflight txns can progress
  public boolean checkTxnsForProgress(Graph g, boolean[] exclude) {
    if (inflightTxns.size() > 0 && log)
      System.out.println("Checking amongst " + inflightTxns.size() + "txns");

    boolean progressed = true;
    while (progressed) {
      progressed = false;
      if (log) System.out.println("going till txns progress once more");

      // go through txns and see if any can complet
      for (Transaction t : this.inflightTxns) {
        double timeSinceTxnArrival = curTime - t.time;
        double txnCompletionRate = (t.originalVal - t.val) / timeSinceTxnArrival;
        double projectedTotalTime = (optimization && txnCompletionRate > 0) ? t.originalVal / txnCompletionRate : -1;
        /*if (projectedTotalTime != -1) System.out.println("Projected total time for " + t  + " is " + projectedTotalTime);*/
        if (curTime > t.time + deadline || projectedTotalTime > (deadline + BUFFER_TIME)) {
          try {
            txn_outcome_file.append("Failed at time " + curTime + " for " +
                    t.count + "(" + t.time + ") " +
                    t.originalVal + " " + (t.originalVal - t.val) + "\n");
          } catch (Exception e) {
            e.printStackTrace();
          }
          continue;
        }

        double originalVal = t.val;
        double completedAmt = this.routingAlg.route(t, g, exclude, this.isPacket, curTime);
        //if (log) System.out.println("Completed " + completedAmt + " for " + t);
        if (completedAmt > epsilon) {
          //succeeded
          if (log) System.out.println("Succeeded  partially for " + t + " by " + completedAmt);
          progressed = true;

          // queue a completion for the partial progress
          eventQueue.offer(new RoutingEvent(curTime + this.txnDelay,
                  RoutingEventType.TXN_COMPLETION, new Transaction(t)));
        }
        updateMetrics(t, originalVal, completedAmt, false, false);
      }

      this.inflightTxns = new TreeSet<Transaction>(this.newInflightTxns);
      this.newInflightTxns.clear();
    }
    // will be false when it gets out
    return progressed;
  }

  public void outputOutstandingTransactions() {
    try {
      for (Transaction t : inflightTxns) {
        fail_file.append(t.count + " " + t.val + "\n");
        txn_outcome_file.append("Failed at time " + curTime + " for " +
                t.count + "(" + t.time + ") " +
                t.originalVal + " " + (t.originalVal - t.val) + "\n");
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Override
  public void computeData(Graph g, Network n, HashMap<String, Metric> m) {
    Treeembedding embed = null;

    if (this.routingAlg == RoutingAlgorithmTypes.SILENTWHISPERS ||
            this.routingAlg == RoutingAlgorithmTypes.SPEEDYMURMURS) {
      System.out.println("roots are " + Arrays.toString(roots));
      embed = new Treeembedding("T", 60, roots, MultipleSpanningTree.Direct.TWOPHASE);
      if (!g.hasProperty("SPANNINGTREE_0")) {
        g = embed.transform(g);
      }
    }

    int count = 0;
    int epoch_old = 0;
    int epoch_cur = 0;

    success_first = 0;
    success = 0;
    Vector<Double> succR = new Vector<Double>();
    Vector<Integer> stabMes = new Vector<Integer>();
    Vector<Double> txnAmounts = new Vector<Double>();
    Node[] nodes = g.getNodes();
    boolean[] exclude = new boolean[nodes.length];

    //go over transactions
    LinkedList<Transaction> toRetry = new LinkedList<Transaction>();
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

    int start = 0;
    int limit = 2000;
    linkBalances = new double[edgecapacities.getWeights().size()][limit/*this.transactions.size()*/];

    // open files to log credit link data
    openTimeSeriesFiles(edgecapacities);

    int txn_counter = 0;
    int intervalNum = 0;
    for (Transaction t : this.transactions) {
      txn_counter++;
      //if (log) System.out.println("reading txn");
      if (t.val > epsilon) {// ignore really small transactions altogether
        eventQueue.offer(new RoutingEvent(t.time, RoutingEventType.TXN_ARRIVAL, t));
        //if (log) System.out.println("offering txn to event queue");
      }

      // queue demand update events
      // TODO: Recomputation
                            /*if (t.time / updateInterval > intervalNum) {
                                intervalNum++;
                                eventQueue.offer(new RoutingEvent(t.time, 
                                            RoutingEventType.DEMAND_UPDATE, null));

                            }*/
    }

    if (log) System.out.println("finished adding txns as events");

    while (eventQueue.size() > 0) {

      RoutingEvent nextEvent = eventQueue.poll();

      if (nextEvent.getType() == RoutingEventType.TXN_ARRIVAL) {
        writeImbalanceData(edgecapacities, c, start, limit);
        c++;
      }

      handleEvent(nextEvent, g, exclude);

      boolean firstTry = false;

      epoch_cur = (int) Math.floor(curTime / epoch);

      // update any newly replenished or added links
      if (!this.newLinks.isEmpty()) { /* potentially use this for adding imbalance */
        double nt = this.newLinks.peek()[0];
        while (nt <= curTime) {
          double[] link = this.newLinks.poll();
          this.addLink((int) link[1], (int) link[2], new double[]{link[3], link[4], link[5]}, g);
          nt = this.newLinks.isEmpty() ? Double.MAX_VALUE : this.newLinks.peek()[0];
        }
        System.out.println("adding new links");
      }

      count++;


      //check if and how many spanning tree re-construction took place since last transaction
      //do 1 (!) re-computation if there was any & set stabilization cost
      if (epoch_cur != epoch_old) {
        if (this.routingAlg == RoutingAlgorithmTypes.SILENTWHISPERS) {
          if (log) {
            System.out.println("Recompute spt");
          }
          for (int i = 0; i < roots.length; i++) {
            g.removeProperty("SPANNINGTREE_" + i);
            g.removeProperty("TREE_COORDINATES_" + i);
          }
          g = embed.transform(g);
        }
      }


      // 3: make progress on inflight txns if possible if you are using a packet approach
                                /*if (this.isPacket) {
                                    boolean progressed = checkTxnsForProgress(g, exclude);
                                    if (c >= transactions.size() && !progressed) {
                                        // no more progress can be made
                                        // because no more transactions that would change anything
                                        // record these failures somehow TODO
                                        System.out.println("NOTHING IS PROGRESSING");
                                        break;
                                    }
                                }*/
      epoch_old = epoch_cur;
    }

    updateMetrics(); // all metrics are done
    this.graph = g;

    if (isPacket)
      outputOutstandingTransactions();

    // close all files
    try {
      succ_file.close();
      imbal_file.close();
      succ_amt_file.close();
      fail_file.close();
      txn_outcome_file.close();

      if (!this.txnFileName.contains("Ripple"))
        for (Edge e : fwMap.keySet())
          fwMap.get(e).close();
    } catch (Exception e) {
      System.out.println("Couldn't close file");
    }

			/*
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
			try{
			    map.saveToFile(new File("java-link-balances." +  start + "_" + limit + "NOTABS.png"));
			} catch (Exception e) {
			    e.printStackTrace();
			    System.out.println("Failed to save heatMap");
			}
                        */


  }

  private void updateMetrics() {
    updateMetrics(null, 0, 0, true /*done*/, false /*firstTry*/);
  }


  private void updateMetrics(Transaction cur, double originalVal, double completedAmt,
                             boolean done, boolean firstTry) {


    // aggregate if all transactions are done
    if (done) {
      this.success = this.success / (double) transactions.size();
      this.success_first = this.success_first / (double) transactions.size();
      this.trials = new Distribution(trys, (int) this.success);
    } else {
      double timeSinceTxnArrival = curTime - cur.time;

      if (originalVal == completedAmt || (completedAmt > originalVal - epsilon &&
              completedAmt < originalVal + epsilon)) {
        completedAmt = originalVal; // to avoid succ ratio > 100
        cur.setTxnComplete();
        cur.updateVal(originalVal - completedAmt);
        num_succ++;
        this.success++;
        if (cur.requeue == 0)
          this.success_first++;
        if (log) System.out.println("marked as completed");

        try {
          txn_outcome_file.append("Succeeded at time " + curTime + " for " +
                  cur.count + "(" + cur.time + ") " +
                  cur.originalVal + " " + (cur.originalVal - cur.val) + "\n");
        } catch (Exception e) {
          e.printStackTrace();
        }
      } else if (this.isPacket) {
        if (completedAmt == 0) {
          double txnCompletionRate = (cur.originalVal - cur.val) / timeSinceTxnArrival;
          double projectedTotalTime = (optimization && txnCompletionRate > 0) ?
                  cur.originalVal / txnCompletionRate : -1;

          /*if (projectedTotalTime != -1) System.out.println("Projected total time for " + cur  + " is " + projectedTotalTime);*/


          //if(log) System.out.println("Nothing progressed");
          //System.out.println("Deadline " + deadline + " Current txn time " + cur.time + " current time" + curTime);
          if (cur.time + deadline > curTime && projectedTotalTime < (deadline + BUFFER_TIME)) {
            newInflightTxns.add(cur);
          } else {
            try {
              fail_file.append(cur.count + " " + cur.val + "\n");
              txn_outcome_file.append("Failed at time " + curTime + " for " +
                      cur.count + "(" + cur.time + ") " +
                      cur.originalVal + " " + (cur.originalVal - cur.val) + "\n");

            } catch (Exception e) {
              e.printStackTrace();
            }

          }
        } else {
          cur.updateVal(originalVal - completedAmt);
          double txnCompletionRate = (cur.originalVal - cur.val) / timeSinceTxnArrival;
          double projectedTotalTime = (optimization && txnCompletionRate > 0) ?
                  cur.originalVal / txnCompletionRate : -1;


          /*if (projectedTotalTime != -1) System.out.println("Projected total time for " + cur  + " is " + projectedTotalTime);*/
          //if (log) System.out.println("Enqueuing remaining***************");
          if (cur.time + deadline > curTime && projectedTotalTime < (deadline + BUFFER_TIME)) {
            newInflightTxns.add(cur);
          } else {
            try {
              fail_file.append(cur.count + " " + cur.val + "\n");
              txn_outcome_file.append("Failed at time " + curTime + " for " +
                      cur.count + "(" + cur.time + ") " +
                      cur.originalVal + " " + (cur.originalVal - cur.val) + "\n");
            } catch (Exception e) {
              e.printStackTrace();
            }
          }

        }
      } else {

        //System.out.println("Txn number " + (num_tries + 1) + "failed");
        completedAmt = 0; // for all practical purposes was a transaction that made no progress
        try {
          fail_file.append(cur.count + " " + cur.val + "\n");
          txn_outcome_file.append("Failed at time " + curTime + " for " +
                  cur.count + "(" + cur.time + ") " +
                  cur.originalVal + " " + (cur.originalVal - cur.val) + "\n");
        } catch (Exception e) {
          e.printStackTrace();
        }
      }

      // update demand matrix by subtracting current value from demand
      String srcDestKey = cur.src + "_" + cur.dst;
      double curVal = demandMatrix.get(srcDestKey);
      demandMatrix.put(srcDestKey, Math.max(0, curVal - completedAmt));

      cur_count++;
      if (completedAmt > 0) {
        amt_succ += completedAmt; // used to compute amount succeeded
        trys = this.inc(trys, cur.requeue);
      }

      /* if this is the first try, make a point reporting the succ ratio*/
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
    }
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
			/*succ &= DataWriter.writeWithIndex(this.transactionMess.getDistribution(),
					this.key+"_MESSAGES", folder);
			succ &= DataWriter.writeWithIndex(this.transactionMessRe.getDistribution(),
					this.key+"_MESSAGES_RE", folder);
			succ &= DataWriter.writeWithIndex(this.transactionMessSucc.getDistribution(),
					this.key+"_MESSAGES_SUCC", folder);
			succ &= DataWriter.writeWithIndex(this.transactionMessFail.getDistribution(),
					this.key+"_MESSAGES_FAIL", folder);
			
			succ &= DataWriter.writeWithIndex(this.pathL.getDistribution(),
					this.key+"_PATH_LENGTH", folder);
			succ &= DataWriter.writeWithIndex(this.pathLRe.getDistribution(),
					this.key+"_PATH_LENGTH_RE", folder);
			succ &= DataWriter.writeWithIndex(this.pathLSucc.getDistribution(),
					this.key+"_PATH_LENGTH_SUCC", folder);
			succ &= DataWriter.writeWithIndex(this.pathLFail.getDistribution(),
					this.key+"_PATH_LENGTH_FAIL", folder);*/

    succ &= DataWriter.writeWithIndex(this.trials.getDistribution(),
            this.key + "_TRIALS", folder);

			
			/*succ &= DataWriter.writeWithIndex(this.path_single.getDistribution(),
					this.key+"_PATH_SINGLE", folder);
			succ &= DataWriter.writeWithIndex(this.path_singleFound.getDistribution(),
					this.key+"_PATH_SINGLE_FOUND", folder);
			succ &= DataWriter.writeWithIndex(this.path_singleNF.getDistribution(),
					this.key+"_PATH_SINGLE_NF", folder);
			succ &= DataWriter.writeWithIndex(this.succs,
					this.key+"_SUCC_RATIOS", folder);
                        succ &= DataWriter.writeWithIndex(this.utilization,
					this.key+"_UTILIZATION", folder);*/


    if (Config.getBoolean("SERIES_GRAPH_WRITE")) {
      (new GtnaGraphWriter()).writeWithProperties(graph, folder + "graph.txt");
    }


    return succ;
  }

  @Override
  public Single[] getSingles() {
			/*Single m_av = new Single("PAYMENT_NETWORK_PACKET_MES_AV", 
                                this.transactionMess.getAverage());
			Single m_Re_av = new Single("PAYMENT_NETWORK_PACKET_MES_RE_AV", 
                                this.transactionMessRe.getAverage());
			Single m_S_av = new Single("PAYMENT_NETWORK_PACKET_MES_SUCC_AV", 
                                this.transactionMessSucc.getAverage());
			Single m_F_av = new Single("PAYMENT_NETWORK_PACKET_MES_FAIL_AV", 
                                this.transactionMessFail.getAverage());
			
			Single p_av = new Single("PAYMENT_NETWORK_PACKET_PATH_AV", 
                                this.pathL.getAverage());
			Single p_Re_av = new Single("PAYMENT_NETWORK_PACKET_PATH_RE_AV", 
                                this.pathLRe.getAverage());
			Single p_S_av = new Single("PAYMENT_NETWORK_PACKET_PATH_SUCC_AV", 
                                this.pathLSucc.getAverage());
			Single p_F_av = new Single("PAYMENT_NETWORK_PACKET_PATH_FAIL_AV", 
                                this.pathLFail.getAverage());
			

			
			Single pP_av = new Single("PAYMENT_NETWORK_PACKET_PATH_SINGLE_AV", 
                                this.path_single.getAverage());
			Single pPF_av = new Single("PAYMENT_NETWORK_PACKET_PATH_SINGLE_FOUND_AV", 
                                this.path_singleFound.getAverage());
			Single pPNF_av = new Single("PAYMENT_NETWORK_PACKET_PATH_SINGLE_NF_AV", 
                                this.path_singleNF.getAverage());*/


    Single s1 = new Single("PAYMENT_NETWORK_PACKET_SUCCESS_DIRECT",
            this.success_first);
    Single s = new Single("PAYMENT_NETWORK_PACKET_SUCCESS", this.success);

                        /*double utilSum = 0;
                        for (double u : utilization){
                            utilSum += u;
                        }*/
    //Single utilization_av = new Single("UTILIZATION", utilSum/utilization.length);
			/*return new Single[]{m_av, m_Re_av, m_S_av, m_F_av,p_av, p_Re_av, p_S_av, p_F_av,
					s1, s, pP_av, pPF_av, pPNF_av, utilization_av};*/
    return new Single[]{s1, s};
  }

  @Override
  public boolean applicable(Graph g, Network n, HashMap<String, Metric> m) {
    return g.hasProperty("CREDIT_LINKS");
  }

  private Vector<Transaction> readList(String file) {
    Vector<Transaction> vec = new Vector<Transaction>();
    int count = 0;
    try {
      BufferedReader br = new BufferedReader(new FileReader(file));
      String line;
      while ((line = br.readLine()) != null) {
        String[] parts = line.split(" ");
        if (parts.length == 4) {
          Transaction ta = new Transaction(Double.parseDouble(parts[0]),
                  Double.parseDouble(parts[1]),
                  Integer.parseInt(parts[2]),
                  Integer.parseInt(parts[3]), count);
          count++;
          vec.add(ta);
        }
        if (parts.length == 3) {
          Transaction ta = new Transaction(0,
                  Double.parseDouble(parts[0]),
                  Integer.parseInt(parts[1]),
                  Integer.parseInt(parts[2]), count);
          count++;
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
