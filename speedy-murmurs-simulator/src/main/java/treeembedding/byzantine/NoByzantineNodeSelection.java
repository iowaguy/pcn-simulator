package treeembedding.byzantine;

import java.util.HashSet;
import java.util.Set;
import java.util.Vector;

import gtna.graph.Node;
import treeembedding.credit.Transaction;

public class NoByzantineNodeSelection extends ByzantineNodeSelection {

  @Override
  public Set<Integer> conscript(Node[] allNodes, Vector<Transaction> transactions) {
    return new HashSet<>();
  }
}
