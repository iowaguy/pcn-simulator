package treeembedding.treerouting;

import gtna.graph.Node;
import treeembedding.credit.CreditLinks;

public class TreerouteSilentW extends Treeroute {
  // if the traversal is going up the tree (which it must do before coming back down)
  boolean up = true;

  public TreerouteSilentW() {
    super("TREE_ROUTE_SILENTW");
  }

  public TreerouteSilentW(int trials) {
    super("TREE_ROUTE_SILENTW", trials);
  }

  public TreerouteSilentW(int trials, int trees, int t) {
    super("TREE_ROUTE_SILENTW", trials, trees, t);
  }

  @Override
  protected NextHopPlusMetrics nextHop(int cur, Node[] nodes, long[] dest, int destN) {
    int index = -1;
    if (up) {
      index = sp.getParent(cur);
      if (index == -1) {
        up = false;
      }
    }
    if (!up) {
      int[] out = sp.getChildren(cur);
      int dbest = this.getCPL(dest, this.coords[cur]);
      for (int i = 0; i < out.length; i++) {
        int cpl = this.getCPL(this.coords[out[i]], dest);
        if (cpl > dbest) {
          dbest = cpl;
          index = out[i];
          break;
        }
      }
    }
    return new NextHopPlusMetrics(index, 0);
  }

  private int getCPL(long[] a, long[] b) {
    int cpl = 0;
    while (cpl < a.length && cpl < b.length && a[cpl] == b[cpl]) {
      cpl++;
    }
    return cpl;
  }

  @Override
  protected NextHopPlusMetrics nextHop(int cur, Node[] nodes, long[] dest, int destN,
                                       boolean[] exclude, int pre, double weight, CreditLinks edgeweights) {
    int index = -1;
    if (up) {
      index = sp.getParent(cur);
      if (index == -1) {
        up = false;
      } else {
        if (index == pre || exclude[index]) {
          return new NextHopPlusMetrics(-1, 0);
        }
      }

    }
    if (!up) {
      int[] out = sp.getChildren(cur);
      int dbest = this.getCPL(dest, this.coords[cur]);
      for (int i = 0; i < out.length; i++) {
        if (!exclude[out[i]]) {
          int cpl = this.getCPL(this.coords[out[i]], dest);
          if (cpl > dbest) {
            dbest = cpl;
            index = out[i];
            break;
          }
        }
      }
    }
    return new NextHopPlusMetrics(index, 0);
  }

  @Override
  protected void initRoute() {
    up = true;

  }
}
