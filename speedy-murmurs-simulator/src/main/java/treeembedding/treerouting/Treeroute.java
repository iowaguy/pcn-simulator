package treeembedding.treerouting;


import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Random;
import java.util.Vector;

import gtna.data.Single;
import gtna.graph.Graph;
import gtna.graph.Node;
import gtna.graph.spanningTree.SpanningTree;
import gtna.io.DataWriter;
import gtna.metrics.Metric;
import gtna.networks.Network;
import gtna.util.Distribution;
import treeembedding.Util;
import treeembedding.credit.CreditLinks;

public abstract class Treeroute extends Metric {
  public static final double MIN_TRANSACTION = 0.0000001;
  int trials;
  double fraction_root;
  double traffic_max;
  double[] traffic;
  private Distribution hopDistribution;
  double avHops = 0;
  long[][] coords;
  Random rand;
  SpanningTree sp;
  int trees;
  int t;

  public Treeroute(String key, int trials, int trees, int t) {
    super(key);
    this.trials = trials;
    this.trees = trees;
    this.t = t;
  }

  public Treeroute(String key, int trials) {
    this(key, trials, 1, 1);
  }

  public Treeroute(String key) {
    super(key);
    rand = new Random();
  }


  @Override
  public void computeData(Graph g, Network n, HashMap<String, Metric> m) {
    //get coordinates and root, init distributions
    this.fraction_root = 0;
    this.traffic = new double[g.getNodeCount()];
    long[] hops = new long[1];
    rand = new Random();
    Node[] nodes = g.getNodes();
    //route trail times
    for (int i = 0; i < trials; i++) {
      int src = rand.nextInt(nodes.length);
      int dest = rand.nextInt(nodes.length);
      while (dest == src) {
        dest = rand.nextInt(nodes.length);
      }
      //System.out.println("src " + src + "dest " + dest);
      int[] l = Util.getkOfN(trees, t, rand);
      int h = Integer.MAX_VALUE;
      for (int j = 0; j < l.length; j++) {
        int[] path = this.getRoute(src, dest, l[j], g, nodes);
        int root = this.sp.getSrc();
        for (int s = 0; s < path.length; s++) {
          traffic[path[s]]++;
          if (path[s] == root) {
            this.fraction_root = this.fraction_root + 1 / (double) (l.length);
          }
        }
        if (path.length - 1 < h) {
          h = path.length - 1;
        }
      }
      hops = this.inc(hops, h);
//			int r = rand.nextInt(this.trees);
//			coords = ((TreeCoordinates)g.getProperty("TREE_COORDINATES_"+r)).coords;
//			sp = (SpanningTree) g.getProperty("SPANNINGTREE_"+r);
//			int root = sp.getSrc();
//			int[] destC = this.coords[dest];
//			int s=0;
//			while (src != dest){
//				s++;
//				if (src == root){
//					this.fraction_root++;
//				}
//				traffic[src]++;
//				src = this.nextHop(src, nodes,destC,dest);
//			}
//			if (src == root){
//				this.fraction_root++;
//			}
//			traffic[src]++;
//			hops = this.inc(hops, s);
    }
    this.hopDistribution = new Distribution(hops, trials);
    this.avHops = this.hopDistribution.getAverage();
    this.fraction_root = this.fraction_root / (double) trials;
    this.traffic_max = 0;
    for (int i = 0; i < traffic.length; i++) {
      traffic[i] = traffic[i] / trials;
      if (traffic[i] > traffic_max) {
        traffic_max = traffic[i];
      }
    }
  }

  private int[] getRoute(int src, int dest, int r, Graph g, Node[] nodes) {
    coords = ((TreeCoordinates) g.getProperty("TREE_COORDINATES_" + r)).coords;
    sp = (SpanningTree) g.getProperty("SPANNINGTREE_" + r);
    int root = sp.getSrc();
    long[] destC = this.coords[dest];
    Vector<Integer> route = new Vector<Integer>();
    route.add(src);
    initRoute();
    while (src != dest) {
      src = this.nextHop(src, nodes, destC, dest);
      route.add(src);
    }
    int[] path = new int[route.size()];
    for (int i = 0; i < route.size(); i++) {
      path[i] = route.get(i);
    }
    return path;
  }

  public int[] getRoute(int src, int dest, int r, Graph g, Node[] nodes, boolean[] exclude) {
    return getRoute(src, dest, r, g, nodes, exclude, null, 0);
  }

  public int[] getRoute(int src, int dest, int r, Graph g, Node[] nodes, boolean[] exclude,
                        CreditLinks edgeweights, double weight) {
    coords = ((TreeCoordinates) g.getProperty("TREE_COORDINATES_" + r)).coords;
    sp = (SpanningTree) g.getProperty("SPANNINGTREE_" + r);
    int root = sp.getSrc();
    long[] destC = this.coords[dest];
    Vector<Integer> route = new Vector<>();
    route.add(src);
    initRoute();
    int previousHop = -1;
    boolean done = false;
    while (!done) {
      int next;
      if (edgeweights == null) {
        next = this.nextHop(src, nodes, destC, dest, exclude, previousHop, weight, edgeweights);
      } else {
        next = this.nextHopWeight(edgeweights, src, nodes, destC, dest, exclude, previousHop, weight);
      }
      route.add(next);
      previousHop = src;
      src = next;
      if (root == src) {
        this.fraction_root++;
      }
      if (src == -1) {
        done = true;
      }
      if (src == dest) {
        done = true;
      }
    }
    int[] path = new int[route.size()];
    for (int i = 0; i < route.size(); i++) {
      path[i] = route.get(i);
    }
    return path;
  }

  public int[] getRouteBacktrack(int src, int dest, int r, Graph g, Node[] nodes, boolean[] exclude) {
    coords = ((TreeCoordinates) g.getProperty("TREE_COORDINATES_" + r)).coords;
    sp = (SpanningTree) g.getProperty("SPANNINGTREE_" + r);
    int root = sp.getSrc();
    long[] destC = this.coords[dest];
    Vector<Integer> route = new Vector<Integer>();
    route.add(src);
    int pre = -1;
    HashMap<Integer, LinkedList<Integer>> nexts = new HashMap<Integer, LinkedList<Integer>>();
    HashMap<Integer, Integer> pres = new HashMap<Integer, Integer>();
    boolean newNode = true;
    initRoute();
    while (src != dest && src != -1) {
      if (newNode) {
        LinkedList<Integer> nextL = this.nextHops(src, nodes, destC, dest, exclude, pre, null);
        if (nextL.size() > 0) nexts.put(src, nextL);
        pres.put(src, pre);
      }
      Integer next;
      LinkedList<Integer> nextL = nexts.get(src);
      if (nextL != null) {
        next = nextL.removeFirst();
        if (nextL.size() == 0) nexts.remove(src);
        if (pres.containsKey(next)) {
          route.add(next);
          next = src;
          newNode = false;
        } else {
          newNode = true;
        }
      } else {
        next = pres.get(src);
        newNode = false;
        if (next == null) {
          next = -1;
        }
      }

      pre = src;
      src = next;
      if (root == src) {
        this.fraction_root++;
      }
      route.add(next);
    }
    int[] path = new int[route.size()];
    for (int i = 0; i < route.size(); i++) {
      path[i] = route.get(i);
    }
    return path;
  }

  public int[] getRouteBacktrack(int src, int dest, int r, Graph g, Node[] nodes, boolean[] exclude,
                                 CreditLinks edgeweights, double weight) {
    coords = ((TreeCoordinates) g.getProperty("TREE_COORDINATES_" + r)).coords;
    sp = (SpanningTree) g.getProperty("SPANNINGTREE_" + r);
    int root = sp.getSrc();
    long[] destC = this.coords[dest];
    Vector<Integer> route = new Vector<Integer>();
    route.add(src);
    int pre = -1;
    HashMap<Integer, LinkedList<Integer>> nexts = new HashMap<Integer, LinkedList<Integer>>();
    HashMap<Integer, Integer> pres = new HashMap<Integer, Integer>();
    boolean newNode = true;
    initRoute();
    while (src != dest && src != -1) {
      if (newNode) {
        LinkedList<Integer> nextL = this.nextHopsWeight(edgeweights, src, nodes, destC, dest, exclude, pre, weight);
        if (nextL.size() > 0) nexts.put(src, nextL);
        pres.put(src, pre);
      }
      Integer next;
      LinkedList<Integer> nextL = nexts.get(src);
      if (nextL != null) {
        next = nextL.removeFirst();
        if (nextL.size() == 0) nexts.remove(src);
        if (pres.containsKey(next)) {
          route.add(next);
          next = src;
          newNode = false;
        } else {
          newNode = true;
        }
      } else {
        next = pres.get(src);
        newNode = false;
        if (next == null) {
          next = -1;
        }
      }

      pre = src;
      src = next;
      if (root == src) {
        this.fraction_root++;
      }
      route.add(next);
    }
    int[] path = new int[route.size()];
    for (int i = 0; i < route.size(); i++) {
      path[i] = route.get(i);
    }
    return path;
  }

  /**
   * @return the fraction_root
   */
  public double getFraction_root() {
    return fraction_root;
  }

  @Override
  public boolean writeData(String folder) {
    boolean success = true;
    success &= DataWriter.writeWithIndex(
            this.hopDistribution.getDistribution(),
            this.key + "_HOP_DISTRIBUTION", folder);
    success &= DataWriter.writeWithIndex(
            this.hopDistribution.getCdf(),
            this.key + "_HOP_DISTRIBUTION_CDF", folder);
    success &= DataWriter.writeWithIndex(
            this.traffic,
            this.key + "_TRAFFIC", folder);
    for (int i = 0; i < traffic.length; i++) {
      traffic[i] = -1 * traffic[i];
    }
    Arrays.sort(traffic);
    for (int i = 0; i < traffic.length; i++) {
      traffic[i] = -1 * traffic[i];
    }
    success &= DataWriter.writeWithIndex(
            this.traffic,
            this.key + "_TRAFFIC_SORTED", folder);
    return success;
  }

  @Override
  public Single[] getSingles() {
    Single r = new Single(this.key + "_ROOT_TRAFFIC", this.fraction_root);
    Single mt = new Single(this.key + "_MAX_TRAFFIC", this.traffic_max);
    Single av = new Single(this.key + "_HOPS_AVERAGE", this.avHops);
    return new Single[]{r, mt, av};
  }

  @Override
  public boolean applicable(Graph g, Network n, HashMap<String, Metric> m) {
    return g.hasProperty("TREE_COORDINATES_0");
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

  protected abstract int nextHop(int cur, Node[] nodes, long[] destID, int dest);

  protected abstract int nextHop(int cur, Node[] nodes, long[] destID, int dest, boolean[] exclude, int pre, double weight, CreditLinks edgeweights);

  protected abstract void initRoute();

  private LinkedList<Integer> nextHops(int cur, Node[] nodes, long[] destID, int dest, boolean[] exclude, int pre, CreditLinks edgeweights) {
    LinkedList<Integer> list = new LinkedList<Integer>();
    int add = this.nextHop(cur, nodes, destID, dest, exclude, pre, 0.0, edgeweights);
    while (add != -1) {
      list.add(add);
      exclude[add] = true;
      add = this.nextHop(cur, nodes, destID, dest, exclude, pre, 0.0, edgeweights);
    }
    for (int i = 0; i < list.size(); i++) {
      exclude[list.get(i)] = false;
    }
    return list;
  }

  // method is synchronized to prevent concurrent modification of exclude array
  private synchronized LinkedList<Integer> nextHopsWeight(CreditLinks edgeWeights, int cur, Node[] nodes, long[] destID, int dest, boolean[] exclude, int previousHop, double weight) {
    LinkedList<Integer> confirmedNextHops = new LinkedList<>();
    LinkedList<Integer> inspectedNextHops = new LinkedList<>();
    int add = this.nextHop(cur, nodes, destID, dest, exclude, previousHop, weight, edgeWeights);
    while (add != -1) {
      if (edgeWeights.getMaxTransactionAmount(cur, add) >= weight - MIN_TRANSACTION) {
        confirmedNextHops.add(add);
      }
      inspectedNextHops.add(add);
      exclude[add] = true;
      add = this.nextHop(cur, nodes, destID, dest, exclude, previousHop, weight, edgeWeights);
    }
    for (Integer checkedNextHop : inspectedNextHops) {
      exclude[checkedNextHop] = false;
    }
    return confirmedNextHops;
  }

  private int nextHopWeight(CreditLinks edgeWeights, int cur, Node[] nodes, long[] destID, int dest, boolean[] exclude, int previousHop, double weight) {
    LinkedList<Integer> list = this.nextHopsWeight(edgeWeights, cur, nodes, destID, dest, exclude, previousHop, weight);
    if (list.isEmpty()) {
      return -1;
    } else {
      return list.poll();
    }
  }


}
