package treeembedding.byzantine;

import java.util.Set;
import java.util.Vector;

import gtna.graph.Node;
import treeembedding.credit.Transaction;

abstract class ByzantineNodeSelection {
  int numByzantineNodes = 0;
  Set<Integer> selectedByzantineNodes;

  abstract Set<Integer> conscript(Node[] allNodes, Vector<Transaction> transactions);

  void setNumByzantineNodes(int numByzantineNodes) {
    this.numByzantineNodes = numByzantineNodes;
  }

  void setSelectedByzantineNodes(Set<Integer> selectedByzantineNodes) {
    this.selectedByzantineNodes = selectedByzantineNodes;
  }
}
