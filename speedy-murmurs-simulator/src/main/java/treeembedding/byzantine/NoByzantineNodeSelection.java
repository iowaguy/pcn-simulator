package treeembedding.byzantine;

import java.util.HashSet;
import java.util.Set;

import gtna.graph.Node;

public class NoByzantineNodeSelection extends ByzantineNodeSelection {

  @Override
  public Set<Integer> conscript(Node[] allNodes) {
    return new HashSet<>();
  }
}
