package treeembedding.credit;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import gtna.graph.Edge;
import treeembedding.RoutingAlgorithm;
import treeembedding.credit.exceptions.InsufficientFundsException;
import treeembedding.credit.exceptions.TransactionFailedException;

public class LinkWeight {
  static final double EPSILON = 0.000000001;
  private final double initial;
  private Edge edge;
  private double min;
  private double max;
  private double current;
  private Map<Double, Integer> pendingTransactions;
  private final double u; // used in BCD metric
  private final double l; // used in BCD metric

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
    this.initial = 0;
    this.pendingTransactions = new ConcurrentHashMap<>();
    this.u = 0.0;
    this.l = 0.0;
  }

  LinkWeight(Edge edge, double min, double max, double current) {
    this.edge = edge;
    this.min = min;
    this.max = max;
    this.current = current;
    this.initial = current;
    this.unlockedMax = max;
    this.unlockedMin = min;
    this.pendingTransactions = new ConcurrentHashMap<>();

    // calculate L and U for BCD
    double l1 = Math.abs(max - initial);
    double l2 = Math.abs(min - initial);
    this.l = Math.abs(Math.min(l1, l2));
    this.u = Math.abs(Math.max(l1, l2));
  }

  private static boolean areDoublesEqual(double d1, double d2) {
    double diff = d1 - d2;
    return diff <= EPSILON && diff >= -EPSILON;
  }

  public Edge getEdge() {
    return edge;
  }

  double getDeviation() {
    return Math.abs(this.current - this.initial);
  }

  double getBCD() {
    return getDeviation() * Math.abs(u + l) / (u);
  }

  public boolean isLiquidityExhausted(double weight) {
    // would link have had enough credit to support tx if it wasn't collateralized?
    double potentialWeight = getCurrent() - weight;
    if (weight > 0) {
      return (potentialWeight > getMin()) && (potentialWeight < getUnlockedMin());
    } else {
      return (potentialWeight < getMax()) && (potentialWeight > getUnlockedMax());
    }
  }

  boolean isZero() {
    return ((max - current < EPSILON) && (max - current > -EPSILON)) ||
            ((current - min < EPSILON) && (current - min > -EPSILON));
  }

  void undoUpdateWeight(double weightChange, RoutingAlgorithm.Collateralization collateralization)
          throws TransactionFailedException {
    finalizeUpdateWeight(weightChange, collateralization, true);
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

  double getUnlockedMax() {
    return this.unlockedMax;
  }

  private void setUnlockedMax(double unlockedMax) {
    this.unlockedMax = unlockedMax;
  }

  double getUnlockedMin() {
    return this.unlockedMin;
  }

  private void setUnlockedMin(double unlockedMin) {
    this.unlockedMin = unlockedMin;
  }

  private synchronized void strictlyCollateralizeFunds(double lockAmount) {
    if (lockAmount < 0) {
      // increase unlocked min by -lockAmount
      setUnlockedMax(getUnlockedMax() + lockAmount);
    } else {
      // increase unlocked max by lockAmount
      setUnlockedMin(getUnlockedMin() + lockAmount);
    }
  }

  private synchronized void strictlyDecollateralizeFunds(double unlockAmount) {
    if (unlockAmount < 0) {
      // decrease unlocked max by lockAmount
      setUnlockedMax(getUnlockedMax() - unlockAmount);
    } else {
      // decrease unlocked min by lockAmount
      setUnlockedMin(getUnlockedMin() - unlockAmount);
    }
  }

  private synchronized void totallyCollateralizeFunds() {
    setUnlockedMin(getCurrent());
    setUnlockedMax(getCurrent());
  }

  private synchronized void totallyDecollateralizeFunds() {
    setUnlockedMin(getMin());
    setUnlockedMax(getMax());
  }

  private double getEffectiveMax(RoutingAlgorithm.Collateralization collateralization) {
    if (collateralization == RoutingAlgorithm.Collateralization.STRICT ||
            collateralization == RoutingAlgorithm.Collateralization.TOTAL) {
      return getUnlockedMax();
    } else {
      return getMax();
    }
  }

  private double getEffectiveMin(RoutingAlgorithm.Collateralization collateralization) {
    if (collateralization == RoutingAlgorithm.Collateralization.STRICT ||
            collateralization == RoutingAlgorithm.Collateralization.TOTAL) {
      return getUnlockedMin();
    } else {
      return getMin();
    }
  }

  double getMaxTransactionAmount(boolean isForward, RoutingAlgorithm.Collateralization collateralizationType) {
    return getMaxTransactionAmount(isForward, collateralizationType, null, 0);
  }

  // if funds are being sent from a lower numbered node to a higher numbered node, the transaction
  // is considered "forward"
  double getMaxTransactionAmount(boolean isForward, RoutingAlgorithm.Collateralization collateralizationType,
                                 CreditMaxFlow maxFlowCN, int currentEpoch) {
    double mta;
    if (isForward) {
      mta = getCurrent() - getEffectiveMin(collateralizationType);
      // check if max transaction size is zero
      if (maxFlowCN != null &&
              (areDoublesEqual(mta, 0.0)) &&
              // if the effective min is non-zero and the maximum transaction size is zero, this
              // means that the transaction will be blocked due to liquidity exhaustion. Update
              // metric accordingly. This segment only applies to maxflow, because in maxflow *any*
              // amount of collateralization on the link will cause the routing to be different.
              // INVARIANT: min will always be less than or equal to unlockedMin
              !(areDoublesEqual(this.min, this.unlockedMin)) &&
              !(areDoublesEqual(this.min, getCurrent()))) {
        maxFlowCN.incrementPerEpochValue(AbstractCreditNetworkBase.Metrics.BLOCKED_LINKS_PER_EPOCH, currentEpoch);
      }
    } else {
      mta = getEffectiveMax(collateralizationType) - getCurrent();
      // check if max transaction size is zero
      if (maxFlowCN != null &&
              (areDoublesEqual(mta, 0.0)) &&
              // if there is some collateral locked up and the maximum transaction size is zero,
              // this means that the transaction will be blocked due to liquidity exhaustion. Update
              // metric accordingly. This segment only applies to maxflow, because in maxflow *any*
              // amount of collateralization on the link will cause the routing to be different.
              // INVARIANT: max will always be greater or equal to unlockedMax
              !(areDoublesEqual(this.max, this.unlockedMax)) &&
              !(areDoublesEqual(this.max, getCurrent()))) {
        maxFlowCN.incrementPerEpochValue(AbstractCreditNetworkBase.Metrics.BLOCKED_LINKS_PER_EPOCH, currentEpoch);
      }
    }
    return mta;
  }

  boolean areFundsAvailable(double weightChange, RoutingAlgorithm.Collateralization collateralization) {
    double newPotentialWeight = getCurrent() - weightChange;

    return newPotentialWeight <= getEffectiveMax(collateralization) &&
            newPotentialWeight >= getEffectiveMin(collateralization);
  }

  synchronized void prepareUpdateWeight(double weightChange, RoutingAlgorithm.Collateralization collateralization)
          throws InsufficientFundsException {
    if (!areFundsAvailable(weightChange, collateralization)) {
      throw new InsufficientFundsException();
    }

    // if key is not in map, put 1 as value, otherwise add 1 to the current value
    this.pendingTransactions.merge(weightChange, 1, Integer::sum);
    if (collateralization == RoutingAlgorithm.Collateralization.STRICT) {
      strictlyCollateralizeFunds(weightChange);
    } else if (collateralization == RoutingAlgorithm.Collateralization.TOTAL) {
      totallyCollateralizeFunds();
    }
  }

  synchronized void finalizeUpdateWeight(double weightChange, RoutingAlgorithm.Collateralization collateralization)
          throws TransactionFailedException {
    finalizeUpdateWeight(weightChange, collateralization, false);
  }

  private synchronized void finalizeUpdateWeight(double weightChange, RoutingAlgorithm.Collateralization collateralization, boolean undo)
          throws TransactionFailedException {
    if (this.pendingTransactions.containsKey(weightChange)) {
      int num = this.pendingTransactions.get(weightChange);
      if (num > 1) {
        this.pendingTransactions.put(weightChange, num - 1);
      } else {
        this.pendingTransactions.remove(weightChange);
      }
    } else if (undo) {
      return;
    } else {
      throw new TransactionFailedException("Cannot finalize a transaction that wasn't prepared");
    }

    if (collateralization == RoutingAlgorithm.Collateralization.STRICT) {
      strictlyDecollateralizeFunds(weightChange);
    } else if (collateralization == RoutingAlgorithm.Collateralization.TOTAL) {
      totallyDecollateralizeFunds();
    }


    if (!undo) {
      updateCurrent(weightChange);
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