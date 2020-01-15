package treeembedding.treerouting;

import java.util.Vector;

import gtna.graph.Node;
import treeembedding.credit.CreditLinks;
import treeembedding.credit.LinkWeight;

public abstract class TreerouteNH extends Treeroute {

  TreerouteNH(String key, int trials, int trees, int t) {
    super(key);
    this.trials = trials;
    this.trees = trees;
    this.t = t;
  }

  TreerouteNH(String key, int trials) {
    this(key, trials, 1, 1);
  }

  TreerouteNH(String key) {
    super(key);
  }

  @Override
  protected NextHopPlusMetrics nextHop(int cur, Node[] nodes, long[] destID, int dest) {
    return nextHop(cur, nodes, destID, dest, null, 0, 0.0, null);
  }

  @Override
  protected NextHopPlusMetrics nextHop(int cur, Node[] nodes, long[] destID, int dest,
                                       boolean[] exclude, int previousHop, double weight, CreditLinks edgeweights) {
    int[] outgoingEdges = nodes[cur].getIncomingEdges();
    double dbest = this.dist(cur, cur, dest);
    Vector<Integer> closest = new Vector<>();
    int blockedLinksCounter = 0;

    for (int neighbor : outgoingEdges) {
      if (exclude == null || (!exclude[neighbor] && previousHop != neighbor)) {
        double dcur = this.dist(cur, neighbor, dest);
        if (dcur <= dbest) {
          if (closest.size() == 0 && dcur == dbest) continue;

          // check if there is enough credit to route transaction
          if (edgeweights.getMaxTransactionAmount(cur, neighbor) < weight - MIN_TRANSACTION) {
            LinkWeight linkWeight = edgeweights.getWeights(cur, neighbor);

            // check if credit is being collateralized. if true then collateralization is causing
            // a reroute, so counter should be incremented
            if (linkWeight.isLiquidityExhausted(weight)) {
              blockedLinksCounter++;
            }
            continue;
          }

          if (dcur < dbest) {
            dbest = dcur;
            closest = new Vector<>();
          }
          closest.add(neighbor);
        }
      }
    }
    int index;
    if (closest.size() == 0) {
      index = -1;
    } else {
      index = closest.get(rand.nextInt(closest.size()));
    }
    return new NextHopPlusMetrics(index, blockedLinksCounter);
  }



  protected abstract double dist(int node, int neighbor, int dest);

  @Override
  protected void initRoute() {


  }

}
