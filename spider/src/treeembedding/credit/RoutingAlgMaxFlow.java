package treeembedding.credit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Vector;

import gtna.graph.Edge;
import gtna.graph.Graph;
import gtna.graph.Node;

public class RoutingAlgMaxFlow extends RoutingAlgorithm {
  private boolean log = false;

  /**************************************************************************************************
   * Maxflow's routing method with residual flow helper
   * @param cur Transaction to route
   * @param g Graph to route on
   * @param exclude nodes to be excluded from route
   * ************************************************************************************************/
  public double route(Transaction cur, Graph g, boolean[] exclude, boolean isPacket, double curTime) {

    CreditLinks edgeweights = (CreditLinks) g.getProperty("CREDIT_LINKS");
    HashMap<Edge, Double> original = new HashMap<Edge, Double>();
    HashMap<Edge, Double> originalInflight = new HashMap<Edge, Double>();
    int src = cur.src;
    int dest = cur.dst;
    int mes = 0;
    int path = 0;
    Vector<Integer> paths = new Vector<Integer>();
    ArrayList<PathAndAmt> txnPathList = new ArrayList<PathAndAmt>();

    double totalflow = 0;
    int[][] resp = new int[0][0];
    while (totalflow < cur.val && (resp = findResidualFlow(edgeweights, g.getNodes(), src, dest)).length > 1) {
      if (log) System.out.println("Found residual flow " + resp[0].length);
      //pot flow along this path
      double min = Double.MAX_VALUE;
      Edge minEdge = new Edge(src, dest);
      int[] respath = resp[0];
      if (log) System.out.println("path for this flow is" + Arrays.toString(respath));
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
        double inflight = edgeweights.getInflight(n1, n2);
        Edge e = n1 < n2 ? new Edge(n1, n2) : new Edge(n2, n1);
        if (!original.containsKey(e)) {
          original.put(e, w);
          originalInflight.put(e, inflight);
        }
        edgeweights.setWeight(n1, n2, min);
        if (log) System.out.println("Updating weight of (" + n1 + "," + n2 + ") by " + min);
      }
      txnPathList.add(new PathAndAmt(respath, min));
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

    if (log) System.out.println("Total flow completed " + totalflow);

    if (!isPacket && cur.val - totalflow > epsilon) {
      //System.exit(1);
      this.weightUpdate(edgeweights, original, originalInflight);
    } else {
      // something went inflight
      this.addInflightTxn(cur, txnPathList);
    }
    return totalflow;
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
        if (pre[n][0] == -1 && ew.getPot(n1, n) > 0) {
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
            if (log) System.out.println("found a path");
            return new int[][]{respath, stats};
          }
          q.add(n);
        }
        if (ew.getPot(n1, n) == 0 && log)
          System.out.println("Edge saturated from " + n1 + " to " + n + "inflight amount " + ew.getInflight(n1, n));

      }
    }
    if (log) System.out.println("No path found");
    return new int[][]{new int[]{mes}};
  }
}
