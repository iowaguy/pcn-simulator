package treeembedding.credit;

public class Transaction {

  int src;
  int dst;
  double val;
  double time;
  int requeue = 0;
  int mes = 0;
  int path = 0;
  boolean txnComplete = false;
  int count = 0;
  double originalVal;

  public Transaction(double t, double v, int s, int d, int count) {
    this.src = s;
    this.dst = d;
    this.val = v;
    this.time = t;
    this.count = count;
    this.originalVal = val;
  }

  public Transaction(double t, double v, int s, int d) {
    this(t, v, s, d, 0);
  }

  public Transaction(Transaction that) {
    this.src = that.src;
    this.dst = that.dst;
    this.val = that.val;
    this.time = that.time;
    this.txnComplete = that.txnComplete;
    this.requeue = that.requeue;
    this.mes = that.mes;
    this.path = that.path;
    this.count = that.count;
    this.originalVal = that.originalVal;
  }

  public void incRequeue(double nexttime) {
    this.requeue++;
    this.time = nexttime;
  }

  public void addPath(int p) {
    this.path = this.path + p;
  }

  public void addMes(int p) {
    this.mes = this.mes + p;
  }

  public void updateVal(double newVal) {
    this.val = newVal;
  }

  public void setTxnComplete() {
    this.val = 0.0;
    this.txnComplete = true;
  }

  @Override
  public int hashCode() {
    int result = 17;

    //result = 31 * result + (txnComplete ? 1 : 0);
    result = 31 * result + src;
    result = 31 * result + dst;
    long timelong = Double.doubleToLongBits(time);
    result = 31 * result + (int) (timelong ^ (timelong >>> 32));
    long vallong = Double.doubleToLongBits(val);
    result = 31 * result + (int) (vallong ^ (vallong >>> 32));
    return result;

  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof Transaction)) {
      return false;
    }
    Transaction t = (Transaction) obj;
    if (t.src == src && t.dst == dst && t.val == val && t.time == time)
      return true;
    return false;
  }

  public String toString() {
    return time + "(" + count + "):" + src + "->" + dst + " (" + val + ")";
  }

}
