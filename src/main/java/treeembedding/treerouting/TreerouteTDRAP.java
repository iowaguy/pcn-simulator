package treeembedding.treerouting;

public class TreerouteTDRAP extends TreerouteNH {

  public TreerouteTDRAP() {
    super("TREE_ROUTE_TDRAP");
  }

  public TreerouteTDRAP(int trials) {
    super("TREE_ROUTE_TDRAP", trials);
  }

  public TreerouteTDRAP(int trials, int trees, int t) {
    super("TREE_ROUTE_TDRAP", trials, trees, t);
  }

  @Override
  protected double dist(int node, int neighbor, int dest) {
    long[] a = this.coords[neighbor];
    long[] b = this.coords[dest];
    int cpl = 0;
    int depth = sp.getDepth(neighbor);
    while (cpl < depth && cpl < b.length && a[cpl] == b[cpl]) {
      cpl++;
    }

    return depth + b.length - 2 * cpl;
  }


}
