package treeembedding.treerouting;

import java.util.HashMap;

import gtna.data.Single;
import gtna.graph.Graph;
import gtna.graph.spanningTree.SpanningTree;
import gtna.io.DataWriter;
import gtna.metrics.Metric;
import gtna.networks.Network;
import gtna.util.Util;

public class TreeStats extends Metric {
  //distribution: the depth of all nodes (each tree is one row in output)
  private double[][] depths;
  //distribution: the depth of leaf nodes (each tree is one row in output)
  private double[][] depthsLeaf;
  //distribution: number of children (each tree is one row in output)
  private double[][] children;
  //average depth (per tree, each value one tree)
  private double[] depthAvs;
  //average depth only leaves (per tree, each value one tree)
  private double[] depthAvsLeaf;
  //average children for non-leaf(!!) nodes (per tree, each value one tree)
  private double[] childrenAvs;
  //average Depth over all trees
  private double depthAv;
  //average Depth over all trees only leaf nodes
  private double depthAvLeaf;
  //average number
  private double childrenAv;
  //number leaves (per tree)
  private double[] leafCount;

  public TreeStats() {
    super("TREE_STATS");
  }

  @Override
  public void computeData(Graph g, Network n, HashMap<String, Metric> m) {
    //figure out number of trees
    int numTrees = 1;
    while (g.hasProperty("SPANNINGTREE_" + numTrees)) {
      numTrees++;
    }
    //get depths, children
    int numNodes = g.getNodeCount();
    int[][] listDepths = new int[numTrees][numNodes];
    int[][] listChildren = new int[numTrees][numNodes];
    int maxDepth = 0;
    int maxChild = 0;
    for (int treeID = 0; treeID < numTrees; treeID++) {
      SpanningTree sp = (SpanningTree) g.getProperty("SPANNINGTREE_" + treeID);
      if (sp == null) {
        //in case there is only one spanning tree without index
        sp = (SpanningTree) g.getProperty("SPANNINGTREE");
      }
      for (int nodeID = 0; nodeID < numNodes; nodeID++) {
        listDepths[treeID][nodeID] = sp.getDepth(nodeID);
        if (listDepths[treeID][nodeID] > maxDepth) {
          maxDepth = listDepths[treeID][nodeID];
        }
        listChildren[treeID][nodeID] = sp.getChildren(nodeID).length;
        if (listChildren[treeID][nodeID] > maxChild) {
          maxChild = listChildren[treeID][nodeID];
        }
      }
    }

    //process info on depths
    this.depthAv = 0;
    this.depthAvLeaf = 0;
    this.depthAvs = new double[numTrees];
    this.depthAvsLeaf = new double[numTrees];
    this.leafCount = new double[numTrees];
    this.depths = new double[numTrees][maxDepth + 1];
    this.depthsLeaf = new double[numTrees][maxDepth + 1];
    for (int treeID = 0; treeID < numTrees; treeID++) {
      for (int nodeID = 0; nodeID < numNodes; nodeID++) {
        // Count nodes in treeID that have a depth of listDepths[treeID][nodeID]
        this.depths[treeID][listDepths[treeID][nodeID]]++;

        // Calculate running sum, will be turned into an average later
        this.depthAvs[treeID] = this.depthAvs[treeID] + listDepths[treeID][nodeID];

        // Check if node has children, if it doesn't, then it's a leaf
        if (listChildren[treeID][nodeID] == 0) {
          // Count leaves in treeID that have a depth of listDepths[treeID][nodeID]
          this.depthsLeaf[treeID][listDepths[treeID][nodeID]]++;

          // Calculate running sum, will be turned into an average later
          this.depthAvsLeaf[treeID] = this.depthAvsLeaf[treeID] + listDepths[treeID][nodeID];

          // Count number of leaves in treeID
          this.leafCount[treeID]++;
        }
      }

      // Turn running sums into averages
      this.depthAvs[treeID] = this.depthAvs[treeID] / numNodes;
      this.depthAvsLeaf[treeID] = this.depthAvsLeaf[treeID] / this.leafCount[treeID];
      for (int curDepth = 0; curDepth < maxDepth + 1; curDepth++) {
        this.depths[treeID][curDepth] = this.depths[treeID][curDepth] / numNodes;
        this.depthsLeaf[treeID][curDepth] = this.depthsLeaf[treeID][curDepth] / this.leafCount[treeID];
      }
    }

    // Average depth of all trees
    this.depthAv = Util.avg(this.depthAvs);
    this.depthAvLeaf = Util.avg(this.depthAvsLeaf);

    //get children stats
    this.childrenAv = 0;
    this.childrenAvs = new double[numTrees];
    this.children = new double[numTrees][maxChild + 1];
    for (int i = 0; i < numTrees; i++) {
      for (int k = 0; k < numNodes; k++) {
        this.children[i][listChildren[i][k]]++;
        if (listChildren[i][k] > 0) {
          this.childrenAvs[i] = this.childrenAvs[i] + listChildren[i][k];
        }
      }
      this.childrenAvs[i] = this.childrenAvs[i] / (numNodes - this.leafCount[i]);
      for (int l = 0; l < maxChild + 1; l++) {
        this.children[i][l] = this.children[i][l] / numNodes;
      }
    }
    this.childrenAv = Util.avg(this.childrenAvs);
  }

  @Override
  public boolean writeData(String folder) {
    boolean succ = DataWriter.writeWithIndex(this.depthAvs, "TREE_STATS_AV_DEPTH", folder);
    succ &= DataWriter.writeWithIndex(this.depthAvsLeaf, "TREE_STATS_AV_DEPTH_LEAF", folder);
    succ &= DataWriter.writeWithIndex(this.childrenAvs, "TREE_STATS_AV_CHILDREN", folder);
    succ &= DataWriter.writeWithIndex(this.leafCount, "TREE_STATS_LEAF_COUNT", folder);
    succ &= DataWriter.writeWithoutIndex(this.depths, "TREE_STATS_DEPTH", folder);
    succ &= DataWriter.writeWithoutIndex(this.depthsLeaf, "TREE_STATS_DEPTH_LEAF", folder);
    succ &= DataWriter.writeWithoutIndex(this.children, "TREE_STATS_CHILDREN", folder);

    return succ;
  }

  @Override
  public Single[] getSingles() {
    Single d = new Single("TREE_STATS_AV_DEPTH_ALL", this.depthAv);
    Single dl = new Single("TREE_STATS_AV_DEPTH_LEAF_ALL", this.depthAvLeaf);
    Single c = new Single("TREE_STATS_AV_CHILDREN_ALL", this.childrenAv);
    return new Single[]{d, dl, c};
  }

  @Override
  public boolean applicable(Graph g, Network n, HashMap<String, Metric> m) {
    return g.hasProperty("SPANNINGTREE") || g.hasProperty("SPANNINGTREE_0");
  }
}
