package treeembedding.credit;

class Transaction implements Comparable<Transaction> {
  int index;
  int src;
  int dst;
  double val;

  // some arbitrary JVM time, start time in nanoseconds
  double startTime;

  // some arbitrary JVM time, end time in nanoseconds
  double endTime;

  // this is the timestamp defined in the data set, it has nothing to do with simulator time
  double time;

  int timesRequeued = 0;
  int mes = 0;
  int path = 0;

  Transaction(double t, double v, int s, int d, int i) {
    this.src = s;
    this.dst = d;
    this.val = v;
    this.time = t;
    this.index = i;
  }

  void incRequeue(double nexttime) {
    this.timesRequeued++;
    this.time = nexttime;
  }

  void addPath(int p) {
    this.path = this.path + p;
  }

  void addMes(int p) {
    this.mes = this.mes + p;
  }

  @Override
  public String toString() {
    return "src=" + src + "; dest=" + dst + "; val=" + val + "; time=" + time;
  }

  @Override
  public int compareTo(Transaction o) {
    return Double.compare(time, o.time);
  }
}
