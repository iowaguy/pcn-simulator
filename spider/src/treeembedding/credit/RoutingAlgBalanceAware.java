package treeembedding.credit;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import gtna.graph.Edge;
import gtna.graph.Graph;

public class RoutingAlgBalanceAware extends RoutingAlgorithm {
  private boolean log = false;
  private boolean firstTime = true;

  HashMap<String, ArrayList<PathAndWeight>> srcDestToPathFlows =
          new HashMap<String, ArrayList<PathAndWeight>>();
  HashMap<String, Double> srcDestToDemand = new HashMap<String, Double>();

  // to reset in case of partial completion
  HashMap<Edge, Double> originalWeight = new HashMap<Edge, Double>();


  public class PathAndWeight {
    ArrayList<Integer> path;
    double weight;

    public PathAndWeight(ArrayList<Integer> path, double weight) {
      this.path = path;
      this.weight = weight;
    }

    public ArrayList<Integer> getPath() {
      return path;
    }

    public double getWeight() {
      return weight;
    }

    public String toString() {
      String output = "[";
      for (Integer i : path)
        output += i + ", ";
      output += "] : " + weight;
      return output;
    }
  }

  @Override
  public void initialSetup(boolean rerun, int intervalNum) {
    int K = this.numPaths;
    String optimalOutputFilename = "optimal_paths/opt_" + ((int) this.credit);
    optimalOutputFilename += this.txnFileName.substring(0, this.txnFileName.lastIndexOf('.'));
    optimalOutputFilename += "_" + K;


    File outputFile = new File(optimalOutputFilename);
    if (!outputFile.exists() || rerun) {
      // run lp solver
      // get runtime, exec, waitfor, then parse and blah blah
      Runtime rt = Runtime.getRuntime();

      String demandInput;
      if (firstTime)
        demandInput = "/home/ubuntu/efs/synthetic_data/" + this.txnFileName;
      else {
        File temp = new File(this.txnFileName);
        demandInput = "/home/ubuntu/lightning_routing/speedy/src/demandMatrix" + intervalNum + temp.getName();
      }

      String cmd = "python /home/ubuntu/lightning_routing/simulations/path_based_lp_global/lp.py";
      cmd += " " + demandInput + " ";
      cmd += ((int) this.credit);

      System.out.println(cmd);

      try {
        Process pr = rt.exec(cmd);
        int result = pr.waitFor();
        System.out.println("solving process terminated with " + result);
      } catch (Exception e) {
        e.printStackTrace();
      }
    }

    // fix paths
    parseLPPathOutput(optimalOutputFilename);
  }

  // parse the python output and cosntruct paths from it
  private void parseLPPathOutput(String filename) {
    HashMap<String, ArrayList<PathAndWeight>> pathFlows = new HashMap<String, ArrayList<PathAndWeight>>();

    try {
      BufferedReader br = new BufferedReader(new FileReader(filename));
      String line;
      String srcDestKey = "";
      ArrayList<PathAndWeight> curListOfPaths = new ArrayList<PathAndWeight>();

      while ((line = br.readLine()) != null) {
        if (log) System.out.println(line);

        String[] parts = line.split(" ");
        if (parts.length == 1) {
          //finished a set
          srcDestToPathFlows.put(srcDestKey, curListOfPaths);
          curListOfPaths = new ArrayList<PathAndWeight>();
        } else if (line.contains("(")) {
          // src dest line
          int src = Integer.parseInt(parts[0].substring(1, parts[0].length() - 1));
          int dest = Integer.parseInt(parts[1].substring(0, parts[1].length() - 1));
          srcDestKey = src + "_" + dest;
          double totalDemand = Double.parseDouble(parts[2]);
          if (totalDemand < 0)
            if (log) System.out.println("Some demands are negative");
          srcDestToDemand.put(srcDestKey, totalDemand);
        } else if (parts.length > 0) {
          // line with path and weight
          double weight = Double.parseDouble(parts[parts.length - 1]);
          if (weight < 0) {
            weight = 0;
          }
          String path = String.join("", parts);
          String[] nodes = path.substring(1, path.lastIndexOf(']')).split(",");
          ArrayList<Integer> thisPath = new ArrayList<Integer>();
          for (String n : nodes) {
            thisPath.add(Integer.parseInt(n));
          }
          PathAndWeight thisPathWeight = new PathAndWeight(thisPath, weight);
          curListOfPaths.add(thisPathWeight);
        }
      }
      br.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
    System.out.println("Finished parsing");
  }

  /**************************************************************************************************
   * LP Solution based approach to routing the payments
   * @param cur Transaction to route
   * @param g Graph to route on
   * @param exclude nodes to be excluded from route
   * ************************************************************************************************/
  public double route(Transaction cur, Graph g, boolean[] exclude, boolean isPacket, double curTime) {
    CreditLinks edgeCapacities = (CreditLinks) g.getProperty("CREDIT_LINKS");

    if (firstTime) {
      if (this.txnFileName.contains("Ripple"))
        this.credit = edgeCapacities.getTotalCredit() / edgeCapacities.getWeights().size();

      initialSetup(false, 0);
      firstTime = false;
    }

    // TODO: Is there a packet notion here? I don't think so
    double[] splits = computeSplitsForTxn(cur, edgeCapacities);
    if (splits != null) {
      // make sure min is non-zero
      boolean nonZeroMin = false;
      double[] mins = computeMinsOnPaths(cur, edgeCapacities);
      for (double m : mins)
        if (m > 0) {
          nonZeroMin = true;
          break;
        }
      if (!nonZeroMin) {
        return 0;
      }

      int bottleneckIndex = computeBottleneck(mins, splits);

      // rescale everything with regards to the bottleneck if there is one
      double[] newsplits = rescale(splits, mins, bottleneckIndex);


      // either completed entire txn in proportion to flows
      // or fails entirely and remaining is retried later if it is packet version
      double completed = executeSplitsForTxn(cur, newsplits, edgeCapacities);
      if (log && completed > 0 && (completed < cur.val - epsilon || completed > cur.val + epsilon) &&
              completed != cur.val) {

        if (bottleneckIndex != -1) {
          System.out.println("***************\nMins: " + Arrays.toString(mins));
          System.out.println("TRANSACTION IN CONTEXT " + cur);
          System.out.println("bottleneck " + bottleneckIndex);
          System.out.println("Original split " + Arrays.toString(splits));
          System.out.println("New splits " + Arrays.toString(newsplits));
          System.out.println("Completing " + completed + " for " + cur);
        }
      }
      return completed;
    } else {
      return 0;
    }
  }

  private double[] rescale(double[] splits, double[] mins, int bottleneckIndex) {
    if (bottleneckIndex == -1)
      return splits;

    double ratio = mins[bottleneckIndex] / splits[bottleneckIndex];
    double[] newsplits = new double[splits.length];
    for (int i = 0; i < splits.length; i++)
      newsplits[i] = splits[i] * ratio;

    return newsplits;
  }


  // compute minimums on a set of paths
  private double[] computeMinsOnPaths(Transaction t, CreditLinks edgeCapacities) {
    String srcDestKey = t.src + "_" + t.dst;
    ArrayList<PathAndWeight> setOfPaths = srcDestToPathFlows.get(srcDestKey);

    double[] mins = new double[setOfPaths.size()];
    int i = 0;
    for (PathAndWeight pw : setOfPaths) {
      mins[i] = this.getAvailableBal(pw.getPath(), edgeCapacities);
      i++;
    }
    return mins;

  }

  // compute the bottleneckindex
  private int computeBottleneck(double[] mins, double splits[]) {
    int minIndex = -1;
    double minRatio = Double.POSITIVE_INFINITY;

    double[] ratios = new double[splits.length];

    for (int i = 0; i < splits.length; i++) {
      ratios[i] = mins[i] / splits[i];
    }

    for (int i = 0; i < ratios.length; i++) {
      if (splits[i] == 0)
        continue;
      if (ratios[i] < minRatio) {
        minIndex = i;
        minRatio = ratios[i];
      }
    }
    if (minRatio < 1)
      return minIndex;
    else
      return -1;
  }

  // sample path based on the flows passed in and return index picked
  private int sample(double[] flows, double totalDemand) {
    Double[] weights = new Double[flows.length + 1];

    double sum = 0.0;
    int i = 0;
    for (double f : flows) {
      sum += f;
      i++;
      weights[i] = f / totalDemand;
    }
    weights[i] = (totalDemand - sum) / totalDemand;

    // copy to retrieve indices later
    ArrayList<Double> originalWeights = new ArrayList<Double>(Arrays.asList(weights));

    // construct cdf
    Arrays.sort(weights);
    HashMap<Double, Integer> weightsToIndex = new HashMap<Double, Integer>();
    for (i = 1; i < flows.length + 1; i++) {
      int originalIndex = originalWeights.indexOf(weights[i]);
      originalWeights.remove(weights[i]);
      weights[i] += weights[i - 1];
      weightsToIndex.put(weights[i], originalIndex);
    }

    double randomNumber = Math.random();

    for (i = 0; i < flows.length + 1; i++) {
      if (randomNumber < weights[i]) {
        return weightsToIndex.get(weights[i]);
      }
    }

    // should not be encountered
    throw new UnsupportedOperationException("sampling error");
  }

  // sample proportionately to the flows determined by the lp to compute how to split a given txn across
  // different paths
  private double[] computeSplitsForTxn(Transaction t, CreditLinks edgeCapacities) {
    String srcDestKey = t.src + "_" + t.dst;
    ArrayList<PathAndWeight> setOfPaths = srcDestToPathFlows.get(srcDestKey);
    double totalDemand = srcDestToDemand.get(srcDestKey);
    if (setOfPaths == null) {
      System.out.println("set of paths is null");
      return null;
    }

    if (t.val < 0) {
      if (log) System.out.println("negative txn " + t);
      return null;
      //System.exit(1);
    }

    double[] flows = new double[setOfPaths.size()];
    int i = 0;
    double sumWeights = 0;
    for (PathAndWeight pw : setOfPaths) {
      flows[i] = pw.getWeight();
      sumWeights += flows[i];
      i++;
    }

        /* ONE METHOD OF SAMPLING
        // if pathId is the last index, txn is rejected
        int pathId = sample(flows, totalDemand);
        if (pathId > flows.length)
            return null;
        for (i = 0; i < flows.length; i++){
            if (i == pathId)
                flows[i] = t.val;
            else
                flows[i] = 0;
        }
        */

    // normalize by value and the sum of the weights actually
    // implies transactions are never rejected and you divide txn
    // proprotionate to the expected flows
    if (sumWeights > 0) {
      for (i = 0; i < flows.length; i++) {
        flows[i] = flows[i] * t.val / sumWeights;
      }
    } else {
      if (log) System.out.println("No flow expected on these paths for Transaction" + t);
      return null;
    }

    if (log) System.out.println("flows for transaction " + t + "are " + Arrays.toString(flows));

    return flows;
  }

  // execute all the computed splits for the given transaction
  private double executeSplitsForTxn(Transaction t, double[] splits, CreditLinks edgeCapacities) {
    String srcDestKey = t.src + "_" + t.dst;
    int totalPathLength = 0;
    double totalAmt = 0.0;
    ArrayList<PathAndAmt> txnPathList = new ArrayList<PathAndAmt>();

    originalWeight.clear();

    ArrayList<PathAndWeight> setOfPaths = srcDestToPathFlows.get(srcDestKey);
    for (int i = 0; i < setOfPaths.size(); i++) {
      ArrayList<Integer> path = setOfPaths.get(i).getPath();
      if (splits[i] > 0) {
        if (executeSingleTxnSplit(path, splits[i], edgeCapacities)) {
          txnPathList.add(new PathAndAmt(path, splits[i]));
          totalAmt += splits[i];
        } else {
          if (log) System.out.println("**********something non-zero failed");
          //totalAmt = 0;
          //break; // don't partially complete one split alone
        }
      }
      totalPathLength += path.size();
    }

    if (log) System.out.println("sending a total amount of " + totalAmt + "for " + t);
    if (totalAmt > 0) {
      if (totalAmt != t.val)
        if (log) System.out.println("further issues with value not being equal to the value sent");
      this.addInflightTxn(t, txnPathList);
    }
    return totalAmt;
  }

  // go through the path for this split of the transaction and decrement
  // capacities on all edges from the src to dest
  private boolean executeSingleTxnSplit(ArrayList<Integer> pathForSplit, double splitValue,
                                        CreditLinks edgeCapacities) {
    int curNode = pathForSplit.get(0).intValue();

    if (splitValue <= 0)
      return false;

    int i;
    for (i = 1; i < pathForSplit.size(); i++) {
      int nextNode = pathForSplit.get(i).intValue();

      Edge e = edgeCapacities.makeEdge(curNode, nextNode);
      if (!originalWeight.containsKey(e)) {
        double w = edgeCapacities.getWeight(curNode, nextNode);
        originalWeight.put(e, w);
      }

      if (!edgeCapacities.setWeight(curNode, nextNode, splitValue))
        break; // should update the difference - but failed
      curNode = nextNode;
    }

    if (i == pathForSplit.size())
      return true;
    else {
      //update weight;
      //this.weightUpdate(edgeCapacities, originalWeight, null);
      //System.out.println("failed to send " + splitValue + "on path " + pathForSplit);
      return false;
    }
  }

}
