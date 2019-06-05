package treeembedding.credit;

import java.util.Map;

import gtna.graph.Edge;

public class TransactionResults {

  // res[0], success
  private boolean success;

  // res[1], sum of path lengths for a transaction
  private int sumPathLength;

  // res[2], this represents the sum of the destination depths times the number of landmarks
  // x = x + destDepth*numLandmarks
  // a (destination, landmark) pair is only included if a path to the destination was established
  private int sumReceiverLandmarks;

  // res[3], this represents the sum of the source node depths
  // x = x + srcDepth
  // a (source, landmark) pair is only included if a path to the destination was established
  private int sumSourceDepths;

  // res[4], this is a crazy thing. it is a sum of sumPathLengths, sumReceiverLandmarks,
  // and sumSourceDepths, plus the sum of 2 times all the path lengths for non-zero transaction
  // amounts. I think this might be an attempt to sum all the messages, but I'm pretty sure it's
  // being done incorrectly
  private int res4;

  // res[5]
  private int maxPathLength;

  // res[6:pathlength]
  private int[] pathLengths;

  private Map<Edge, LinkWeight> modifiedEdges;

  TransactionResults(int numRoots) {
    this.pathLengths = new int[numRoots];
    this.sumPathLength = 0;
    this.maxPathLength = 0;
  }

  public boolean isSuccess() {
    return success;
  }

  public void setSuccess(boolean success) {
    this.success = success;
  }

  int getSumPathLength() {
    return sumPathLength;
  }

  void addSumPathLength(int add) {
    this.sumPathLength += add;
  }

  Map<Edge, LinkWeight> getModifiedEdges() {
    return modifiedEdges;
  }

  void setModifiedEdges(Map<Edge, LinkWeight> modifiedEdges) {
    this.modifiedEdges = modifiedEdges;
  }

  int getSumSourceDepths() {
    return sumSourceDepths;
  }

  void addSumSourceDepths(int sumSourceDepths) {
    this.sumSourceDepths += sumSourceDepths;
  }

  int getSumReceiverLandmarks() {
    return sumReceiverLandmarks;
  }

  void addSumReceiverLandmarks(int sumReceiverLandmarks) {
    this.sumReceiverLandmarks += sumReceiverLandmarks;
  }

  int getMaxPathLength() {
    return maxPathLength;
  }

  void setMaxPathLength(int maxPathLength) {
    this.maxPathLength = maxPathLength;
  }

  int getRes4() {
    return res4;
  }

  void setRes4(int res4) {
    this.res4 = res4;
  }

  void addPathLength(int pathIndex, int pathLength) {
    this.pathLengths[pathIndex] = pathLength;
  }

  int[] getPathLengths() {
    return this.pathLengths;
  }
}
