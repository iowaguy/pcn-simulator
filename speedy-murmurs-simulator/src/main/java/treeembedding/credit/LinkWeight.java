package treeembedding.credit;

import gtna.graph.Edge;

public class Link {
  private Edge edge;
  private double min;
  private double max;
  private double current;

  // these are the funds that are not tied up in any concurrent transactions
  private double unlocked;

  Link(Edge edge) {
    this.edge = edge;
    this.min = 0;
    this.max = 0;
    this.current = 0;
    this.unlocked = 0;
  }

  Link(Edge edge, double min, double max, double current) {
    this.edge = edge;
    this.min = min;
    this.max = max;
    this.current = current;
  }

  public double getMin() {
    return min;
  }

  public void setMin(double min) {
    this.min = min;
  }

  public double getMax() {
    return max;
  }

  public void setMax(double max) {
    this.max = max;
  }

  public double getCurrent() {
    return current;
  }

  public synchronized void setCurrent(double current) {
    this.current = current;
  }
  public double getUnlocked() {
    return unlocked;
  }

  public synchronized void lockFunds(double lockAmount) {
    //if ()
    this.unlocked = lockAmount;
  }

  double getMaxTransactionAmount() {
    if (edge.getSrc() < edge.getDst()) {
      return getMax() - getCurrent();
    } else {
      return getCurrent() - getMin();
    }
  }

  @Override
  public int hashCode() {
    return this.edge.toString().hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof Link) {
      return o.hashCode() == this.hashCode();
    } else {
      return false;
    }
  }
}