package treeembedding.byzantine;

import java.util.HashSet;
import java.util.Set;

import gtna.graph.Node;

public class RandomByzantineNodeSelection extends ByzantineNodeSelection {

  /**
   * @param numByzantineNodes the number of byzantine nodes to conscript
   */
  public RandomByzantineNodeSelection(int numByzantineNodes) {
    this.numByzantineNodes = numByzantineNodes;
  }

  /**
   * Select byzantine nodes randomly
   *
   * @param allNodes an array of all the nodes in the graph
   * @return a set of all the nodes selected to be byzantine
   */
  @Override
  public Set<Integer> conscript(Node[] allNodes) {
    Set<Integer> ret = new HashSet<>();
    for (int i = 0; i < numByzantineNodes; i++) {
      ret.add(i);
    }
    return ret;
  }
}
