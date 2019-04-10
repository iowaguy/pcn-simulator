package treeembedding.credit;

import java.util.ArrayList;
import java.util.HashMap;

import gtna.algorithms.shortestPaths.BreadthFirstSearch;
import gtna.graph.Graph;

public class RoutingAlgShortest extends RoutingAlgorithm {
  private int K = 1;
  private boolean log = false;
  private HashMap<String, ArrayList<Integer>> shortestPaths = new HashMap<String, ArrayList<Integer>>();
  private boolean firstTime = true;
  BreadthFirstSearch bfs = new BreadthFirstSearch();


  /**************************************************************************************************
   * heursitic created by us to route on a set of shortest paths while tracking the balance across
   *  them such that they are all equally imbalanced at any given point
   * @param cur Transaction to route
   * @param g Graph to route on
   * @param exclude nodes to be excluded from route
   * ************************************************************************************************/
  public double route(Transaction cur, Graph g, boolean[] exclude, boolean isPacket, double curTime) {
    CreditLinks edgeCapacities = (CreditLinks) g.getProperty("CREDIT_LINKS");

    String srcDestKey = cur.src + "_" + cur.dst;
    ArrayList<Integer> path;
    if (shortestPaths.containsKey(srcDestKey))
      path = shortestPaths.get(srcDestKey);
    else {
      path = bfs.getShortestPathForSrcDest(g, cur.src, cur.dst);
      shortestPaths.put(srcDestKey, path);
    }

    if (log) System.out.println("path from " + cur.src + " to " + cur.dst + " is " + path);

    if (path.size() == 0) //no path - should not happen because one large connected component
      return 0;

    double valAvailable = getAvailableBal(path, edgeCapacities);

    if (!isPacket && valAvailable < cur.val) {
      return 0;
    }

    double valToSend = Math.min(valAvailable, cur.val);
    executeSingleTxn(path, cur, valToSend, edgeCapacities);

    return valToSend;
  }

  // go through the path for this split of the transaction and decrement
  // capacities on all edges from the src to dest
  private void executeSingleTxn(ArrayList<Integer> path, Transaction t, double valToSend,
                                CreditLinks edgeCapacities) {
    int curNode = path.get(0).intValue();

    //if(log) System.out.println("Sending " + splitValue + "on this path" + pathForSplit);

    if (t.val == 0)
      return;

    for (int i = 1; i < path.size(); i++) {
      int nextNode = path.get(i).intValue();
      edgeCapacities.setWeight(curNode, nextNode, valToSend); // should update the difference
      curNode = nextNode;
    }

    if (log) System.out.println("sending a total amount of " + valToSend + "for " + t);

    ArrayList<PathAndAmt> txnPathList = new ArrayList<PathAndAmt>();
    txnPathList.add(new PathAndAmt(path, valToSend));
    this.addInflightTxn(t, txnPathList);
  }

}
