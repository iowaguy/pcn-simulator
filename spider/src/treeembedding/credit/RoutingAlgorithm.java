package treeembedding.credit;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;

import gtna.graph.Edge;
import gtna.graph.Graph;

public abstract class RoutingAlgorithm {
  public class PathAndAmt {
    int[] path;
    double inflightAmt; // for this path

    public PathAndAmt(int[] path, double amt) {
      this.path = Arrays.copyOf(path, path.length);
      this.inflightAmt = amt;
    }

    public PathAndAmt(ArrayList<Integer> path, double amt) {
      this.path = path.stream().mapToInt(Integer::intValue).toArray();
      this.inflightAmt = amt;
    }
  }

  private HashMap<Transaction, ArrayList<PathAndAmt>> inflightTxnPaths =
          new HashMap<Transaction, ArrayList<PathAndAmt>>();

  public static final double epsilon = 1e-10;
  public double credit;
  public String txnFileName;
  public static final double DEADLINE = 5.0; // seconds
  public static final double TXN_RATE = 1000.0; // per second

  int numPaths = 4;


  public void setParams(double credit, String txnFileName, int numPaths) {
    this.credit = credit;
    this.txnFileName = txnFileName;
    this.numPaths = numPaths;
  }

  public void addInflightTxn(Transaction t, ArrayList<PathAndAmt> pathList) {
    this.inflightTxnPaths.put(new Transaction(t), pathList);
  }

  public void weightUpdate(CreditLinks edgeweights, HashMap<Edge, Double> updateWeight,
                           HashMap<Edge, Double> updateInflight) {
    Iterator<Entry<Edge, Double>> it = updateWeight.entrySet().iterator();
    while (it.hasNext()) {
      Entry<Edge, Double> entry = it.next();
      edgeweights.setWeight(entry.getKey(), entry.getValue());
    }

    if (updateInflight == null)
      return;

    it = updateInflight.entrySet().iterator();
    while (it.hasNext()) {
      Entry<Edge, Double> entry = it.next();
      edgeweights.setInflight(entry.getKey(), entry.getValue());
    }
  }


  public abstract double route(Transaction cur, Graph g, boolean[] exclude, boolean isPacket, double curTime);

  public void initialSetup(boolean rerun, int num) {}

  public boolean releaseInflightFundsForTxn(Transaction t, CreditLinks edgeweights) {
    if (t.val == 0)
      return false;
    ArrayList<PathAndAmt> txnPaths = inflightTxnPaths.get(t);
    if (inflightTxnPaths.remove(t) == null)
      System.out.println("couldn't remove");


    if (txnPaths == null) {
      System.out.println("cannot find entry for Transaction " + t);
      for (Transaction i : inflightTxnPaths.keySet())
        System.out.println(i);

      System.exit(1);
      return false;
    }

    for (PathAndAmt p : txnPaths) {
      int start = p.path[0];
      for (int i = 1; i < p.path.length; i++) {
        int next = p.path[i];
        edgeweights.updateInflight(start, next, -1 * p.inflightAmt);
        start = next;
      }
    }
    return true;
  }


  // find the edge with the minimum capacity on this particular path from src to sink
  // that represents the available balance on that path
  public double getAvailableBal(ArrayList<Integer> path, CreditLinks edgeCapacities) {
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

  // parse a file to get paths from them
  public HashMap<String, ArrayList<ArrayList<Integer>>> parsePaths(String filename) {
    HashMap<String, ArrayList<ArrayList<Integer>>> ksp = new
            HashMap<String, ArrayList<ArrayList<Integer>>>();
    try {
      BufferedReader br = new BufferedReader(new FileReader(filename));
      String line;
      String srcDestKey = "";
      ArrayList<ArrayList<Integer>> curListOfPaths = new ArrayList<ArrayList<Integer>>();

      while ((line = br.readLine()) != null) {
        String[] parts = line.split(" ");
        if (parts.length == 1) {
          //finished a set
          ksp.put(srcDestKey, curListOfPaths);
          curListOfPaths = new ArrayList<ArrayList<Integer>>();
        } else if (line.contains("(")) {
          // src dest line
          int src = Integer.parseInt(parts[0].substring(1, parts[0].length() - 1));
          int dest = Integer.parseInt(parts[1].substring(0, parts[1].length() - 1));
          srcDestKey = src + "_" + dest;
        } else if (parts.length > 0) {
          // line with path
          String path = String.join("", parts);
          String[] nodes = path.substring(1, path.lastIndexOf(']')).split(",");
          ArrayList<Integer> thisPath = new ArrayList<Integer>();
          for (String n : nodes) {
            thisPath.add(Integer.parseInt(n));
          }
          curListOfPaths.add(thisPath);
        }
      }
      br.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
    return ksp;
  }
}
