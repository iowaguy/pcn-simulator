package treeembedding.credit;

import java.util.Comparator;

public enum TransactionComparators implements Comparator<Transaction> {
  RemValueComparator {
    @Override
    public int compare(Transaction t1, Transaction t2) {
      if (Double.compare(t1.val, t2.val) != 0)
        return Double.compare(t1.val, t2.val);
      else if (Integer.compare(t1.src, t2.src) != 0)
        return Integer.compare(t1.src, t2.src);
      else if (Integer.compare(t1.dst, t2.dst) != 0)
        return Integer.compare(t1.dst, t2.dst);
      else
        return Double.compare(t1.time, t2.time);

    }
  },

  TimeComparator {
    @Override
    public int compare(Transaction t1, Transaction t2) {
      return Double.compare(t1.time, t2.time);
    }
  }

}
