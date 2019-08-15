package treeembedding.credit;

class  Transaction implements Comparable<Transaction> {

  int src;
  int dst;
  double val;
  double time;
  int requeue = 0;
  int mes = 0;
  int path = 0;

  Transaction(double t, double v, int s, int d) {
    this.src = s;
    this.dst = d;
    this.val = v/1000;
    this.time = t;
  }

  void incRequeue(double nexttime) {
    this.requeue++;
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
