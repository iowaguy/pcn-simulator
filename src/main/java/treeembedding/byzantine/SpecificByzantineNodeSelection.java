package treeembedding.byzantine;

import java.util.Set;
import java.util.Vector;

import gtna.graph.Node;
import treeembedding.credit.Transaction;

public class SpecificByzantineNodeSelection extends ByzantineNodeSelection {

  /**
   * Select byzantine nodes randomly
   *
   * @param allNodes an array of all the nodes in the graph
   * @return a set of all the nodes selected to be byzantine
   */
  @Override
  public Set<Integer> conscript(Node[] allNodes, Vector<Transaction> transactions) {
    return this.selectedByzantineNodes;
  }
}
