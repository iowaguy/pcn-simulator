package treeembedding.credit;

import gtna.graph.Graph;

public enum RoutingAlgorithmTypes {
  MAXFLOW("MAXFLOW", new RoutingAlgMaxFlow()),
  HEURISTIC("HEURISTIC", new RoutingAlgHeuristic()),
  SPEEDYMURMURS("SM", new RoutingAlgSpeedyMurmurs()),
  SILENTWHISPERS("SW", new RoutingAlgSilentWhispers()),
  SHORTEST("SH", new RoutingAlgShortest()),
  BALANCEAWARE("BA", new RoutingAlgBalanceAware()),
  BALANCEAWARE_OPTIMIZED("BA", new RoutingAlgBalanceAware());

  String strRep;
  RoutingAlgorithm alg;

  RoutingAlgorithmTypes(String str, RoutingAlgorithm alg) {
    this.strRep = str;
    this.alg = alg;
  }

  public String toString() {
    return strRep;
  }

  public double route(Transaction cur, Graph g, boolean[] exclude, boolean isPacket, double curTime) {
    switch (this) {
      case MAXFLOW:
      case HEURISTIC:
      case SILENTWHISPERS:
      case SPEEDYMURMURS:
      case BALANCEAWARE:
      case BALANCEAWARE_OPTIMIZED:
      case SHORTEST:
        return alg.route(cur, g, exclude, isPacket, curTime);
      default:
        return 0;
    }
  }

  public boolean releaseInflightFundsForTxn(Transaction cur, CreditLinks edgeweights) {
    switch (this) {
      case MAXFLOW:
      case HEURISTIC:
      case SILENTWHISPERS:
      case SPEEDYMURMURS:
      case BALANCEAWARE:
      case BALANCEAWARE_OPTIMIZED:
      case SHORTEST:
        return alg.releaseInflightFundsForTxn(cur, edgeweights);
      default:
        return false;
    }
  }

  public void setParams(double credit, String txnFileName, int numPaths) {
    alg.setParams(credit, txnFileName, numPaths);
  }

  public void initialSetup(boolean rerun, int num) {
    switch (this) {
      case BALANCEAWARE:
      case BALANCEAWARE_OPTIMIZED:
        alg.initialSetup(true, num);
      default:
        return;
    }
  }
}
