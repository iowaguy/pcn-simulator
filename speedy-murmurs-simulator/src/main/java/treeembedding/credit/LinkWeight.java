package treeembedding.credit;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import gtna.graph.Edge;
import treeembedding.credit.exceptions.InsufficientFundsException;
import treeembedding.credit.exceptions.TransactionFailedException;

public class LinkWeight {
  private Edge edge;
  private double min;
  private double max;
  private double current;
  private Map<Double, Integer> pendingTransactions;
  private static final double EPSILON = 0.000000001;

  // these are the bounds of the funds taking into consideration funds that have been locked up by
  // concurrent transactions
  private double unlockedMax;
  private double unlockedMin;

  LinkWeight(Edge edge) {
    this.edge = edge;
    this.min = 0;
    this.max = 0;
    this.current = 0;
    this.unlockedMax = 0;
    this.unlockedMin = 0;
    this.pendingTransactions = new ConcurrentHashMap<>();
  }

  LinkWeight(Edge edge, double min, double max, double current) {
    this.edge = edge;
    this.min = min;
    this.max = max;
    this.current = current;
    this.unlockedMax = max;
    this.unlockedMin = min;
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
    this.current -= weightChange;
  }

  private double getUnlockedMax() {
    return this.unlockedMax;
  }

  private double getUnlockedMin() {
    return this.unlockedMin;
  }

  private void setUnlockedMin(double unlockedMin) {
    this.unlockedMin = unlockedMin;
  }

  private void setUnlockedMax(double unlockedMax) {
    this.unlockedMax = unlockedMax;
  }

  private synchronized void lockFunds(double lockAmount) {
    if (lockAmount < 0) {
      // increase unlocked min by -lockAmount
      setUnlockedMax(getUnlockedMax() + lockAmount);
    } else {
      // increase unlocked max by lockAmount
      setUnlockedMin(getUnlockedMin() - lockAmount);
    }
  }

  private synchronized void unlockFunds(double unlockAmount) {
    if (unlockAmount < 0) {
      // decrease unlocked min by lockAmount
      setUnlockedMax(getUnlockedMax() - unlockAmount);
    } else {
      // increase unlocked max by lockAmount
      setUnlockedMin(getUnlockedMin() + unlockAmount);
    }
  }

  private double getEffectiveMax(boolean concurrentTransactions) {
    if (concurrentTransactions) {
      return getUnlockedMax();
    } else {
      return getMax();
    }
  }

  private double getEffectiveMin(boolean concurrentTransactions) {
    if (concurrentTransactions) {
      return getUnlockedMin();
    } else {
      return getMin();
    }
  }

  // if funds are being sent from a lower numbered node to a higher numbered node, the transaction
  // is considered "forward"
  double getMaxTransactionAmount(boolean isForward, boolean concurrentTransactions) {
    if (isForward) {
      return getCurrent() - getEffectiveMin(concurrentTransactions);
    } else {
      return getEffectiveMax(concurrentTransactions) - getCurrent();
    }
  }

  boolean areFundsAvailable(double weightChange, boolean concurrentTransactions) {
    double newPotentialWeight = getCurrent() - weightChange;

    return newPotentialWeight <= getEffectiveMax(concurrentTransactions) &&
            newPotentialWeight >= getEffectiveMin(concurrentTransactions);
  }

  synchronized void prepareUpdateWeight(double weightChange, boolean concurrentTransactions)
          throws InsufficientFundsException {
    if (!areFundsAvailable(weightChange, concurrentTransactions)) {
      throw new InsufficientFundsException();
    }

    // if key is not in map, put 1 as value, otherwise sum 1 to the current value
    this.pendingTransactions.merge(weightChange, 1, Integer::sum);
    if (concurrentTransactions) {
      lockFunds(weightChange);
    }
  }

  synchronized void finalizeUpdateWeight(double weightChange, boolean concurrentTransactions)
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
      unlockFunds(weightChange);
    }

    updateCurrent(weightChange);
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