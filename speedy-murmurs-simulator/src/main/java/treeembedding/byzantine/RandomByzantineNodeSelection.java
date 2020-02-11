package treeembedding.byzantine;

import java.util.HashSet;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.ThreadLocalRandom;

import gtna.graph.Node;
import treeembedding.credit.Transaction;

public class RandomByzantineNodeSelection extends ByzantineNodeSelection {

  /**
   * Select byzantine nodes randomly
   *
   * @param allNodes an array of all the nodes in the graph
   * @return a set of all the nodes selected to be byzantine
   */
  @Override
  public Set<Integer> conscript(Node[] allNodes, Vector<Transaction> transactions) {
    Set<Integer> ret = new HashSet<>();

    for (int i = 0; i < numByzantineNodes; i++) {
      // generate random number between 0 and allNodes.length
      int randomNum = ThreadLocalRandom.current().nextInt(0, allNodes.length);

      // try again if the random node is already included
      if (ret.contains(randomNum)) {
        i--;
        continue;
      }
      // if that node is not in the set, add it
      ret.add(randomNum);
    }
    return ret;
  }
}
