package treeembedding;

public enum RoutingAlgorithm {
  SILENTWHISPERS(0),
  SPEEDYMURMURS(7),
  MAXFLOW(10);

  private int id;
  RoutingAlgorithm(int id) {
    this.id = id;
  }

  public int getId() {
    return this.id;
  }
}
