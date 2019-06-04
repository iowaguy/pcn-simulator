package treeembedding.credit;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import gtna.graph.Edge;

public class LinkWeight {
  private Edge edge;
  private double min;
  private double max;
  private double current;
  private Map<Double, Integer> pendingTransactions;
  private static final double EPSILON = 0.000000001;

  // these are the funds that are not tied up in any concurrent transactions
  private double unlocked;

  LinkWeight(Edge edge) {
    this.edge = edge;
    this.min = 0;
    this.max = 0;
    this.current = 0;
    this.unlocked = 0;
    this.pendingTransactions = new HashMap<>();
  }

  LinkWeight(Edge edge, double min, double max, double current) {
    this.edge = edge;
    this.min = min;
    this.max = max;
    this.current = current;
    this.unlocked = current;
    this.pendingTransactions = new ConcurrentHashMap<>();
  }

  boolean isZero() {
    return ((max - current < EPSILON) && (max - current > -EPSILON)) ||
            ((current - min < EPSILON) && (current - min > -EPSILON));
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

  private void updateCurrent(double weightChange) {
    this.current += weightChange;
    this.unlocked = this.current;
  }

  private double getUnlocked() {
    return unlocked;
  }

  private synchronized void updateUnlocked(double unlocked) {
    this.unlocked += unlocked;
  }

  double getMaxTransactionAmount() {
    if (edge.getSrc() > edge.getDst()) {
      return getMax() - getCurrent();
    } else {
      return getCurrent() - getMin();
    }
  }

  boolean areFundsAvailable(double weightChange, boolean concurrentTransactions) {
    if (this.edge.getSrc() < this.edge.getDst()) {
      weightChange = -weightChange;
    }

    if (concurrentTransactions) {
      double newPotentialWeight = getUnlocked() + weightChange;
      if (weightChange > 0) {
        return newPotentialWeight <= getMax();
      } else if (weightChange < 0) {
        return newPotentialWeight >= getMin();
      } else {
        return true;
      }
    } else {
      double newPotentialWeight = getCurrent() + weightChange;
      if (weightChange > 0) {
        return newPotentialWeight <= getMax();
      } else if (weightChange < 0) {
        return newPotentialWeight >= getMin();
      } else {
        return true;
      }
    }
  }

  boolean prepareUpdateWeight(double weightChange, boolean concurrentTransactions) {
    if (!areFundsAvailable(weightChange, concurrentTransactions)) {
      return false;
    }

    // if key is not in map, put 1 as value, otherwise sum 1 to the current value
    this.pendingTransactions.merge(weightChange, 1, Integer::sum);
    if (this.edge.getSrc() < this.edge.getDst()) {
      if (concurrentTransactions) {
        updateUnlocked(weightChange);
      } else {
        updateCurrent(weightChange);
      }
    } else {
      if (concurrentTransactions) {
        updateUnlocked(-weightChange);
      } else {
        updateCurrent(-weightChange);
      }
    }
    return true;
  }

  void finalizeUpdateWeight(double weightChange, boolean concurrentTransactions)
          throws TransactionFailedException {
    // remove from pending transactions
    if (this.pendingTransactions.containsKey(weightChange)) {
      int num = this.pendingTransactions.get(weightChange);
      if (num > 1) {
        this.pendingTransactions.put(weightChange, num - 1);
      } else {
        this.pendingTransactions.remove(weightChange);
      }
    } else {
      throw new TransactionFailedException("Cannot finalize a transaction that wasn't prepared");
    }

    if (concurrentTransactions) {
      if (this.edge.getSrc() < this.edge.getDst()) {
        updateCurrent(weightChange);
      } else {
        updateCurrent(-weightChange);
      }
    }
  }

  @Override
  public int hashCode() {
    return this.edge.toString().hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof LinkWeight) {
      return o.hashCode() == this.hashCode();
    } else {
      return false;
    }
  }
}