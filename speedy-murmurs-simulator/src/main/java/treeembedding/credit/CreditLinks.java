package treeembedding.credit;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import gtna.graph.Edge;
import gtna.graph.GraphProperty;
import gtna.io.Filereader;
import gtna.io.Filewriter;
import treeembedding.credit.exceptions.InsufficientFundsException;
import treeembedding.credit.exceptions.TransactionFailedException;

public class CreditLinks extends GraphProperty {

  // the array values of this map represent the following in
  // this order: minimum possible weight, current weight, maximum possible weight
  private Map<Edge, LinkWeight> weights;


  public CreditLinks() {
    this.weights = new HashMap<>();
  }

  static Edge makeEdge(int src, int dst) {
    return src < dst ? new Edge(src, dst) : new Edge(dst, src);
  }

  LinkWeight getWeights(int src, int dst) {
    return this.getWeights(makeEdge(src, dst));
  }

  // returns the LinkWeight object, will be a zeroed object if link doesn't exist. this is because
  // a zeroed link is the same as no link
  LinkWeight getWeights(Edge edge) {
    LinkWeight w = this.weights.get(edge);
    if (w == null) {
      return new LinkWeight(edge);
    }
    return w;
  }

  double getWeight(int src, int dst) {
    return this.getWeight(makeEdge(src, dst));
  }

  double getWeight(Edge edge) {
    return this.getWeights(edge).getCurrent();
  }

  public double getMaxTransactionAmount(int src, int dst) {
    return getMaxTransactionAmount(src, dst, true);
  }

  double getMaxTransactionAmount(int src, int dst, boolean concurrentTransactions) {
    LinkWeight weights = this.getWeights(makeEdge(src, dst));
    return weights.getMaxTransactionAmount(src < dst, concurrentTransactions);
  }


  public Set<Entry<Edge, LinkWeight>> getWeights() {
    return this.weights.entrySet();
  }

  public void setWeights(Map<Edge, LinkWeight> weights) {
    this.weights = weights;
  }

  void setWeight(Edge edge, LinkWeight weight) {
    this.weights.put(edge, weight);
  }

  void prepareUpdateWeight(int src, int dst, double weightChange, boolean concurrentTransactions)
          throws InsufficientFundsException {
    LinkWeight weights = getWeights(src, dst);
    if (weights.areFundsAvailable(weightChange, concurrentTransactions)) {
      weights.prepareUpdateWeight(weightChange, concurrentTransactions);
    } else {
      throw new InsufficientFundsException();
    }
  }

  void finalizeUpdateWeight(int src, int dst, double weightChange, boolean concurrentTransactions)
          throws TransactionFailedException {
    getWeights(src, dst).finalizeUpdateWeight(weightChange, concurrentTransactions);
  }

  public void setBound(int src, int dst, double val) {
    LinkWeight ws = getWeights(src, dst);
    if (src < dst) {
      ws.setMax(val);
    } else {
      ws.setMin(-val);
    }
  }

  void setWeight(Edge e, double val) {
    LinkWeight ws = this.getWeights(e);
    ws.setCurrent(val);
  }

  @Override
  public boolean write(String filename, String key) {
    Filewriter fw = new Filewriter(filename);

    this.writeHeader(fw, this.getClass(), key);

    for (Entry<Edge, LinkWeight> entry : this.weights.entrySet()) {
      LinkWeight w = entry.getValue();
      String ws = w.getMin() + " " + w.getCurrent() + " " + w.getMax();
      fw.writeln(entry.getKey().getSrc() + " " + entry.getKey().getDst() + " " + ws);
    }

    return fw.close();
  }

  @Override
  public String read(String filename) {
    Filereader fr = new Filereader(filename);

    String key = this.readHeader(fr);
    this.weights = new HashMap<>();
    String line = null;
    while ((line = fr.readLine()) != null) {
      String[] parts = line.split(" ");
      if (parts.length < 2) continue;
      Edge e = new Edge(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
      double val_low = Double.parseDouble(parts[2]);
      double val = Double.parseDouble(parts[3]);
      double val_high = Double.parseDouble(parts[4]);
      this.weights.put(e, new LinkWeight(e, val_low, val_high, val));
    }

    fr.close();

    return key;
  }
}
