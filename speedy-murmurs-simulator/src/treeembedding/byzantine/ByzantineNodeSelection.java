package treeembedding.byzantine;

import java.util.Set;

import gtna.graph.Node;

public abstract class ByzantineNodeSelection {
  int numByzantineNodes = 0;

  public abstract Set<Integer> conscript(Node[] allNodes);
}
