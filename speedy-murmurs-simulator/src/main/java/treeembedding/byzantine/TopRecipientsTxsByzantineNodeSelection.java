package treeembedding.byzantine;

import java.util.HashSet;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.Vector;

import gtna.graph.Node;
import treeembedding.credit.Transaction;

public class TopRecipientsTxsByzantineNodeSelection extends ByzantineNodeSelection {
  /**
   * Select byzantine nodes randomly
   *
   * @param allNodes an array of all the nodes in the graph
   * @return a set of all the nodes selected to be byzantine
   */
  @Override
  public Set<Integer> conscript(Node[] allNodes, Vector<Transaction> transactions) {


    int[] txCounts = new int[allNodes.length];

    // sort nodes by most transactions received
    for (Transaction tx : transactions) {
      txCounts[tx.getDestination()]++;
    }

    Queue<NodeTxCountPair> q = new PriorityQueue<>();
    for (Node n : allNodes) {
      q.add(new NodeTxCountPair(n, txCounts[n.getIndex()]));
    }

    Set<Integer> ret = new HashSet<>();
    for (int i = 0; i < numByzantineNodes; i++) {
      NodeTxCountPair pair = q.poll();
      if (pair != null) {
        ret.add(pair.node.getIndex());
      }
    }
    return ret;
  }

  private class NodeTxCountPair implements Comparable<NodeTxCountPair> {
    Node node;
    int txCount;

    NodeTxCountPair(Node n, int txCount) {
      this.node = n;
      this.txCount = txCount;
    }

    @Override
    public int compareTo(NodeTxCountPair o) {
      return Integer.compare(o.txCount, this.txCount);
    }
  }
}
