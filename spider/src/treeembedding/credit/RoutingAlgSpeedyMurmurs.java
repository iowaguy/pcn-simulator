package treeembedding.credit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Random;
import java.util.Vector;

import gtna.graph.Edge;
import gtna.graph.Graph;
import gtna.graph.Node;
import gtna.graph.spanningTree.SpanningTree;
import gtna.transformation.spanningtree.MultipleSpanningTree;
import gtna.transformation.spanningtree.MultipleSpanningTree.Direct;
import treeembedding.credit.partioner.Partitioner;
import treeembedding.credit.partioner.RandomPartitioner;
import treeembedding.treerouting.TreeCoordinates;
import treeembedding.treerouting.Treeroute;
import treeembedding.treerouting.TreerouteTDRAP;


public class RoutingAlgSpeedyMurmurs extends RoutingAlgorithm {
  private boolean log = false;
  private int[] roots = new int[]{13, 27}; // initialize
  private Treeroute ra = new TreerouteTDRAP();
  private Partitioner part = new RandomPartitioner();
  private Vector<Edge> zeroEdges = new Vector<Edge>();

  /**************************************************************************************************
   * SpeedyMurmurs algorithm
   * @param cur Transaction to route
   * @param g Graph to route on
   * @param exclude nodes to be excluded from route
   * @param isPacket is this the packet version or not
   * ************************************************************************************************/
  /**
   * @return amount completed
   */

  public double route(Transaction cur, Graph g, boolean[] exclude, boolean isPacket, double curTime) {
    if (this.txnFileName.contains("Ripple"))
      roots = new int[]{8, 118, 120};

    CreditLinks edgeweights = (CreditLinks) g.getProperty("CREDIT_LINKS");
    Node[] nodes = g.getNodes();
    HashMap<Edge, Double> originalWeight = new HashMap<Edge, Double>();
    HashMap<Edge, Double> originalInflight = new HashMap<Edge, Double>();
    ArrayList<PathAndAmt> txnPathList = new ArrayList<PathAndAmt>();


    int[][] paths = new int[roots.length][];
    int src = cur.src;
    int dest = cur.dst;
    //distribute values on paths
    double[] vals = this.part.partition(g, src, dest, cur.val, roots.length);

    //check if transaction works
    boolean succ = true;
    this.zeroEdges = new Vector<Edge>();
    double amtCompleted = 0;
    for (int j = 0; j < paths.length; j++) {
      if (vals[j] != 0) {
        int s = src;
        int d = dest;
        if (vals[j] < 0) {
          s = dest;
          d = src;
        }
        paths[j] = this.ra.getRoute(s, d, j, g, nodes, exclude, edgeweights, vals[j]);
        //System.out.println("New path with value " + vals[j] + " with length" + paths[j].length + ":") ;
        if (paths[j][paths[j].length - 1] == -1) {
          succ = false; // nothing completed on this path
        } else {
          int l = paths[j][0];
          //System.out.print("path starts with " + l + " ");
          for (int i = 1; i < paths[j].length; i++) {
            int k = paths[j][i];
            Edge e = edgeweights.makeEdge(l, k);
            double w = edgeweights.getWeight(e);
            double inflight = edgeweights.getInflight(e);
            if (!originalWeight.containsKey(e)) {
              originalWeight.put(e, w);
              originalInflight.put(e, inflight);
            }
            edgeweights.setWeight(l, k, vals[j]);
            if (log) {
              System.out.println("----Set weight of (" + l + "," + k + ") to " + edgeweights.getWeight(e)
                      + " (previous " + w + ")");
            }
            if (edgeweights.getPot(l, k) == 0) {
              this.zeroEdges.add(e);
            }
            //System.out.print(k + " ");
            l = k;
          }
          amtCompleted += vals[j];
          txnPathList.add(new PathAndAmt(paths[j], vals[j]));
          if (log) System.out.println("path is " + Arrays.toString(paths[j]));

        }
      }
    }
    if (!succ) {
      if (log) {
        System.out.println("Failure on atleast one path");
      }
    } else {
      this.setZeros(edgeweights, originalWeight);
      if (log) {
        System.out.println("Success");
      }
    }

    if (!isPacket && (amtCompleted < cur.val - epsilon || amtCompleted > cur.val + epsilon)) {
      this.weightUpdate(edgeweights, originalWeight, originalInflight);
    } else if (amtCompleted > epsilon) {
      if (log)
        System.out.println("Adding Entry for " + cur + "for total completion of " + amtCompleted);
      this.addInflightTxn(cur, txnPathList);
    }
    postProcess(g);

    return amtCompleted;
  }

  // remove edges set to 0, update spanning tree as a consequence if you need to
  private void postProcess(Graph g) {
    Node[] nodes = g.getNodes();
    if (this.zeroEdges != null) {
      for (int j = 0; j < this.roots.length; j++) {
        SpanningTree sp = (SpanningTree) g.getProperty("SPANNINGTREE_" + j);
        for (int k = 0; k < this.zeroEdges.size(); k++) {
          Edge e = this.zeroEdges.get(k);
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
            if (log) System.out.println("Repair tree " + j +
                    " at expired edge (" + s + "," + t + ")");

            TreeCoordinates coords = (TreeCoordinates) g.getProperty("TREE_COORDINATES_" + j);
            //ignoring cur_stab computation
            this.repairTree(g, nodes, sp, coords,
                    cut, (CreditLinks) g.getProperty("CREDIT_LINKS"));
          }
        }
      }
    }
  }

  /**
   * reconnect disconnected branch with root subroot
   */
  private int repairTree(Graph graph, Node[] nodes, SpanningTree sp, TreeCoordinates coords,
                         int subroot, CreditLinks ew) {
    //remove old tree info of all descendants of subroot
    int mes = 0;
    Queue<Integer> q1 = new LinkedList<Integer>();
    Queue<Integer> q2 = new LinkedList<Integer>();
    q1.add(subroot);
    while (!q1.isEmpty()) {
      int node = q1.poll();
      int[] kids = sp.getChildren(node);
      for (int i = 0; i < kids.length; i++) {
        mes++;
        q1.add(kids[i]);
      }
      sp.removeNode(node);
      q2.add(node);
    }


    Random rand = new Random();
    MultipleSpanningTree.Direct[] dir = {Direct.BOTH, Direct.EITHER, Direct.NONE};
    for (int k = 0; k < dir.length; k++) {
      int count = q2.size();
      while (count > 0) {
        int node = q2.poll();
        Vector<Integer> bestN = new Vector<Integer>();
        int mind = Integer.MAX_VALUE;
        int[] out = MultipleSpanningTree.potParents(graph, nodes[node],
                dir[k], ew);
        for (int i : out) {
          if (sp.getParent(i) != -2) {
            if (sp.getDepth(i) < mind) {
              mind = sp.getDepth(i);
              bestN = new Vector<Integer>();
            }
            if (sp.getDepth(i) == mind) {
              bestN.add(i);
            }
          }
        }
        if (bestN.size() > 0) {
          mes = mes + MultipleSpanningTree.potParents(graph, nodes[node],
                  Direct.EITHER, ew).length;
          int pa = bestN.get(rand.nextInt(bestN.size()));
          sp.addParentChild(pa, node);
          int[] pa_coord = coords.getCoord(pa);
          int[] child_coord = new int[pa_coord.length + 1];
          for (int i = 0; i < pa_coord.length; i++) {
            child_coord[i] = pa_coord[i];
          }
          child_coord[pa_coord.length] = rand.nextInt();
          coords.setCoord(node, child_coord);
          count = q2.size();
        } else {
          q2.add(node);
          count--;
        }
      }
    }
    return mes;
  }

  private void setZeros(CreditLinks edgeweights, HashMap<Edge, Double> updateWeight) {
    this.zeroEdges = new Vector<Edge>();
    Iterator<Entry<Edge, Double>> it = updateWeight.entrySet().iterator();
    while (it.hasNext()) {
      Entry<Edge, Double> entry = it.next();
      int src = entry.getKey().getSrc();
      int dst = entry.getKey().getDst();
      if (edgeweights.getPot(src, dst) == 0) {
        this.zeroEdges.add(new Edge(src, dst));
      }
      if (edgeweights.getPot(dst, src) == 0) {
        this.zeroEdges.add(new Edge(dst, src));
      }
    }
  }

}

