package treeembedding.byzantine;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import gtna.graph.Node;

public class SpecificByzantineNodeSelection extends ByzantineNodeSelection {

  /**
   * Select byzantine nodes randomly
   *
   * @param allNodes an array of all the nodes in the graph
   * @return a set of all the nodes selected to be byzantine
   */
  @Override
  public Set<Integer> conscript(Node[] allNodes) {
    return this.selectedByzantineNodes;
  }
}
