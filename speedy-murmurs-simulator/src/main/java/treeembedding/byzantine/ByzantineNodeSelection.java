package treeembedding.byzantine;

import java.util.Set;

import gtna.graph.Node;

public abstract class ByzantineNodeSelection {
  int numByzantineNodes = 0;
  Set<Integer> selectedByzantineNodes;

  public abstract Set<Integer> conscript(Node[] allNodes);

  public void setNumByzantineNodes(int numByzantineNodes) {
    this.numByzantineNodes = numByzantineNodes;
  }

  public void setSelectedByzantineNodes(Set<Integer> selectedByzantineNodes) {
    this.selectedByzantineNodes = selectedByzantineNodes;
  }
}
