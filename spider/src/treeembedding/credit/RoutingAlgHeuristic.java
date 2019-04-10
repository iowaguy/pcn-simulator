package treeembedding.credit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.PriorityQueue;

import gtna.algorithms.shortestPaths.BreadthFirstSearch;
import gtna.graph.Graph;

public class RoutingAlgHeuristic extends RoutingAlgorithm {
  private int K = 16;
  private boolean log = false;
  private HashMap<String, ArrayList<ArrayList<Integer>>> ksp = null;


  /**************************************************************************************************
   * heursitic created by us to route on a set of shortest paths while tracking the balance across
   *  them such that they are all equally imbalanced at any given point
   * @param cur Transaction to route
   * @param g Graph to route on
   * @param exclude nodes to be excluded from route
   * ************************************************************************************************/
  public double route(Transaction cur, Graph g, boolean[] exclude, boolean isPacket, double curTime) {
    CreditLinks edgeCapacities = (CreditLinks) g.getProperty("CREDIT_LINKS");

    if (this.ksp == null) {
      if (this.txnFileName.contains("Ripple"))
        this.ksp = this.parsePaths("/home/ubuntu/lightning_routing/simulations/ripple_shortest_paths/"
                + K + "_edge_disjoint_shortest_paths_DynSmall.txt");
        // "_edge_disjoint_shortest_paths.txt");
        //
      else {
        if (log) System.out.println("computing paths");
        BreadthFirstSearch bfs = new BreadthFirstSearch();
        this.ksp = bfs.getKShortestPaths(g, K);
        if (log) System.out.println("finished computing paths");
      }
    }


    double[] splits = computeSplitsForTxn(cur, edgeCapacities, isPacket, curTime);
    if (splits != null || isPacket) {
      double completed = executeSplitsForTxn(cur, splits, edgeCapacities);
      return completed;
    } else {
      // don't go through and change any balances on queue txns
      return 0;
    }
  }

  // compute how a given transaction should be split between its k shortest paths
  // Heuristic: find the path with the highest avialable balance and give it atleast
  // as much as it takes to bring its balance to that of the path with second highest
  // available balance. Then take equally from both until you hit the path with the third
  // highest balance and so on
  private double[] computeSplitsForTxn(Transaction t, CreditLinks edgeCapacities, boolean isPacket,
                                       double curTime) {
    String srcDestKey = t.src + "_" + t.dst;
    ArrayList<ArrayList<Integer>> setOfPaths = ksp.get(srcDestKey);

    if (setOfPaths == null)
      System.out.println(srcDestKey);


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
    double maxCap = edgeCapacities.getTotalCredit() / edgeCapacities.getWeights().size();
    double midpoint = maxCap / 2.0;
    double timeToDeadline = DEADLINE - (curTime - t.time);
    double rT = Double.POSITIVE_INFINITY; //(t.val / timeToDeadline) * (1 / TXN_RATE);
    midpoint = 0;

    //System.out.println("midpoint of capacity: " + midpoint);

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
    double valToAdd = 0;
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
      if (highestBalPath.availBal > midpoint)
        valToAdd = highestBalPath.availBal - Math.max(secondHighestBal, midpoint);
      else {
        double valSentSoFar = t.val - remTxnAmount;
        if (valSentSoFar < rT)
          valToAdd = Math.min(rT - valSentSoFar, highestBalPath.availBal - secondHighestBal);
        else
          break;
      }
      double remTxnDivided = remTxnAmount / (pathToSplitMap.size() + 1);
      valToAdd = Math.min(valToAdd, remTxnDivided);

      for (Integer path : pathToSplitMap.keySet()) {
        pathToSplitMap.put(path, pathToSplitMap.get(path) + valToAdd);
      }
      // add this split amount to the path currently with highest balance
      pathToSplitMap.put(highestBalPath.pathId, valToAdd);
      remTxnAmount -= valToAdd * pathToSplitMap.size();
    }

    // no notion of failed txns so always return the best splits you have

    for (Integer path : pathToSplitMap.keySet())
      splits[path.intValue()] = pathToSplitMap.get(path).doubleValue();

    if (remTxnAmount > 0 && !isPacket) {
      return null;
    }

    return splits;
  }


  // execute all the computed splits for the given transaction
  private double executeSplitsForTxn(Transaction t, double[] splits, CreditLinks edgeCapacities) {
    String srcDestKey = t.src + "_" + t.dst;
    int totalPathLength = 0;
    double totalAmt = 0.0;
    ArrayList<PathAndAmt> txnPathList = new ArrayList<PathAndAmt>();

    ArrayList<ArrayList<Integer>> setOfPaths = ksp.get(srcDestKey);
    int[] res = new int[3 + setOfPaths.size()];
    for (int i = 0; i < setOfPaths.size(); i++) {
      ArrayList<Integer> path = setOfPaths.get(i);
      if (splits != null) {
        executeSingleTxnSplit(path, splits[i], edgeCapacities);
        if (splits[i] > 0)
          txnPathList.add(new PathAndAmt(path, splits[i]));
        totalAmt += splits[i];
      }
      totalPathLength += path.size();
      res[i + 3] = path.size();
    }

    if (splits == null)
      res[0] = -1;
    else
      res[0] = 0; // always should succeed because of precomputation
    res[1] = totalPathLength;
    res[2] = 0; // TODO: NUMBER OF MESSAGES

    if (log) System.out.println("sending a total amount of " + totalAmt + "for " + t);
    this.addInflightTxn(t, txnPathList);
    return totalAmt;
  }

  // go through the path for this split of the transaction and decrement
  // capacities on all edges from the src to dest
  private void executeSingleTxnSplit(ArrayList<Integer> pathForSplit, double splitValue,
                                     CreditLinks edgeCapacities) {
    int curNode = pathForSplit.get(0).intValue();

    //if(log) System.out.println("Sending " + splitValue + "on this path" + pathForSplit);

    if (splitValue == 0)
      return;

    for (int i = 1; i < pathForSplit.size(); i++) {
      int nextNode = pathForSplit.get(i).intValue();
      edgeCapacities.setWeight(curNode, nextNode, splitValue); // should update the difference
      curNode = nextNode;
    }
  }

}
