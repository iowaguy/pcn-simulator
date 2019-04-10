package treeembedding.credit;

import java.util.ArrayList;
import java.util.HashMap;

import gtna.graph.Edge;
import gtna.graph.Graph;
import gtna.graph.Node;
import treeembedding.credit.partioner.Partitioner;
import treeembedding.credit.partioner.RandomPartitioner;
import treeembedding.treerouting.Treeroute;
import treeembedding.treerouting.TreerouteSilentW;


public class RoutingAlgSilentWhispers extends RoutingAlgorithm {
  private boolean log = false;
  private int[] roots = new int[]{13, 27}; // initialize
  private Treeroute ra = new TreerouteSilentW();
  private Partitioner part = new RandomPartitioner();


  /**************************************************************************************************
   * SilentWhisper algorithm
   * @param cur Transaction to route
   * @param g Graph to route on
   * @param exclude nodes to be excluded from route
   * @param isPacket is this the packet version or not
   * ************************************************************************************************/
  /**
   * routing using a multi-part computation: costs are i) finding path (one-way, but 3x path length
   * as each neighbor needs to sign its predecessor and successors) ii) sending shares to all
   * landmarks/roots from receiver iii) sending results to sender from all landmarks/roots iv)
   * testing paths (two-way) v) updating credit (one-way)
   *
   * @return amount completed
   */
  public double route(Transaction cur, Graph g, boolean[] exclude, boolean isPacket, double curTime) {
    if (this.txnFileName.contains("Ripple"))
      roots = new int[]{8, 118, 120};

    CreditLinks edgeweights = (CreditLinks) g.getProperty("CREDIT_LINKS");
    Node[] nodes = g.getNodes();
    HashMap<Edge, Double> originalWeight = new HashMap<Edge, Double>();
    HashMap<Edge, Double> originalInflight = new HashMap<Edge, Double>();

    int[][] paths = new int[roots.length][];
    double[] vals;
    int src = cur.src;
    int dest = cur.dst;
    ArrayList<PathAndAmt> txnPathList = new ArrayList<PathAndAmt>();

    //compute paths and minimum credit along the paths
    double[] mins = new double[roots.length];
    for (int j = 0; j < mins.length; j++) {
      paths[j] = ra.getRoute(src, dest, j, g, nodes, exclude);
      String path = "";
      for (int i = 0; i < paths[j].length; i++) {
        path = path + " " + paths[j][i];
      }
      //System.out.println(j + " " + path);
      if (paths[j][paths[j].length - 1] == dest) {
        int l = src;
        int i = 1;
        double min = Double.MAX_VALUE;
        while (i < paths[j].length) {
          int k = paths[j][i];
          double w = edgeweights.getPot(l, k);
          if (w < min) {
            min = w;
          }
          l = k;
          i++;
        }
        mins[j] = min;
        //System.out.println("minimum on this path is" + mins[j]);
      }

    }
    //partition transaction value
    vals = part.partition(g, src, dest, cur.val, mins);

    //check if transaction works
    boolean succ = false;
    double amtCompleted = 0;
    if (vals == null && isPacket)
      vals = mins; // use the minumum value if nothing better can be done

    if (vals != null) {
      succ = true;
      for (int j = 0; j < paths.length; j++) {
        if (isPacket) {
          originalWeight.clear(); // check each subpath individually
          originalInflight.clear();
        }
        //System.out.println("new path with value" + vals[j] + "path length:" +  paths[j].length+ " : " );
        if (vals[j] > 0) {
          if (isPacket) {
            double amt = getAvailableBal(paths[j], edgeweights);
            vals[j] = Math.min(vals[j], amt);
          }
          int l = paths[j][0];
          for (int i = 1; i < paths[j].length; i++) {
            int k = paths[j][i];
            Edge e = edgeweights.makeEdge(l, k);
            double w = edgeweights.getWeight(e);
            double inflight = edgeweights.getInflight(e);
            if (!originalWeight.containsKey(e)) {
              originalWeight.put(e, w);
              originalInflight.put(e, inflight);
            }

            if (!edgeweights.setWeight(l, k, vals[j])) {
              succ = false;
              this.weightUpdate(edgeweights, originalWeight,
                      originalInflight);
              break;
            } else {
              if (log) {
                System.out.println("----Set weight of (" + l + "," + k + ") to " + edgeweights.getWeight(e)
                        + "(previous " + w + ")");
              }
            }
            //System.out.print(l + " ");
            l = k;

          }
          if (succ) {
            amtCompleted += vals[j]; // amount completed on this path
            txnPathList.add(new PathAndAmt(paths[j], vals[j]));
          } else {
            // return to original state
            this.weightUpdate(edgeweights, originalWeight, originalInflight);
            if (!isPacket) break; // not packet, any failure is a complete failure
          }
        }
      }
    } else {
      if (log) {
        System.out.println("Failure");
      }
    }
    if (amtCompleted > 0 && (
            isPacket || (amtCompleted > cur.val - epsilon && amtCompleted < cur.val + epsilon))) {
      this.addInflightTxn(cur, txnPathList);
    }
    return amtCompleted;
  }

  // find the edge with the minimum capacity on this particular path from src to sink
  // that represents the available balance on that path
  private double getAvailableBal(int[] path, CreditLinks edgeCapacities) {
    int curNode = path[0];
    double min = Double.POSITIVE_INFINITY;

    for (int i = 1; i < path.length; i++) {
      int nextNode = path[i];
      double thisEdgeCap = edgeCapacities.getPot(curNode, nextNode);
      if (thisEdgeCap < min) {
        min = thisEdgeCap;
      }
      curNode = nextNode;
    }
    //System.out.println("avail balance on " + path + " is " + min);
    return min;
  }
}

    


