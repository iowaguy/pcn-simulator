package treeembedding.credit;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import gtna.graph.Edge;
import gtna.graph.GraphProperty;
import gtna.io.Filereader;
import gtna.io.Filewriter;

public class CreditLinks extends GraphProperty {

  // the array values of this map represent the following in
  // this order: minimum possible weight, current weight, maximum possible weight
  private Map<Edge, LinkWeight> weights;


  public void setWeights(Map<Edge, LinkWeight> weights) {
    this.weights = weights;
  }


  public CreditLinks() {
    this.weights = new HashMap<>();
  }


  LinkWeight getWeights(int src, int dst) {
    return this.getWeights(makeEdge(src, dst));
  }

  public double getMaxTransactionAmount(int src, int dst) {
    LinkWeight weight = this.getWeights(makeEdge(src, dst));
    return weight.getMaxTransactionAmount();
  }


  public Set<Entry<Edge, LinkWeight>> getWeights() {
    return this.weights.entrySet();
  }

  void setWeight(Edge edge, LinkWeight weight) {
    this.weights.put(edge, weight);
  }

  boolean setWeight(int src, int dst, double weightChange) {
    LinkWeight ws = this.weights.get(makeEdge(src, dst));
    if (src < dst) {
      double dn = ws.getCurrent() + weightChange;
      if (dn <= ws.getMax()) {
        ws.setCurrent(dn);
        return true;
      } else {
        return false;
      }
    } else {
      double dn = ws.getCurrent() - weightChange;
      if (dn >= ws.getMin()) {
        ws.setCurrent(dn);
        return true;
      } else {
        return false;
      }
    }
  }

  public void setBound(int src, int dst, double val) {
    if (src < dst) {
      LinkWeight ws = this.weights.get(new Edge(src, dst));
      ws.setMax(val);
    } else {
      LinkWeight ws = this.weights.get(new Edge(dst, src));
      ws.setMin(-val);
    }
  }

  void setWeight(Edge e, double val) {
    LinkWeight ws = this.weights.get(e);
    ws.setCurrent(val);
  }

  // returns the LinkWeight object, or null if no such link exists
  private LinkWeight getWeights(Edge edge) {
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

  static Edge makeEdge(int src, int dst) {
    return src < dst ? new Edge(src, dst) : new Edge(dst, src);
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
