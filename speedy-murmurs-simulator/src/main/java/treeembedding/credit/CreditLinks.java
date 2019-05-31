package treeembedding.credit;

import java.util.HashMap;
import java.util.Iterator;
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


  public LinkWeight getWeights(int src, int dst) {
    if (src < dst) {
      return this.getWeights(new Edge(src, dst));
    } else {
      return this.getWeights(new Edge(dst, src));
    }
  }

  public double getMaxTransactionAmount(int src, int dst) {
    if (src < dst) {
      LinkWeight weight = this.getWeights(new Edge(src, dst));
      return weight.getMax() - weight.getCurrent();
    } else {
      LinkWeight weight = this.getWeights(new Edge(dst, src));
      return weight.getCurrent() - weight.getMin();
    }
  }


  public Set<Entry<Edge, LinkWeight>> getWeights() {
    return this.weights.entrySet();
  }

  public void setWeight(Edge edge, LinkWeight weight) {
    this.weights.put(edge, weight);
  }

  public boolean setWeight(int src, int dst, double weightChange) {
    if (src < dst) {
      LinkWeight ws = this.weights.get(new Edge(src, dst));
      double dn = ws.getCurrent() + weightChange;
      if (dn <= ws.getMax()) {
        ws.setCurrent(dn);
        return true;
      } else {
        return false;
      }
    } else {
      LinkWeight ws = this.weights.get(new Edge(dst, src));
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

  public void setWeight(Edge e, double val) {
    LinkWeight ws = this.weights.get(e);
    ws.setCurrent(val);
  }

  public LinkWeight getWeights(Edge edge) {
    try {
      return this.weights.get(edge);
    } catch (NullPointerException e) {
      return null;
    }
  }

  public double getWeight(int src, int dst) {
    Edge edge = src < dst ? new Edge(src, dst) : new Edge(dst, src);
    try {
      LinkWeight ws = this.weights.get(edge);
      return ws.getCurrent();
    } catch (NullPointerException e) {
      return 0;
    }
  }

  public double getWeight(Edge edge) {
    try {
      LinkWeight ws = this.weights.get(edge);
      return ws.getCurrent();
    } catch (NullPointerException e) {
      return 0;
    }
  }

  public Edge makeEdge(int src, int dst) {
    return src < dst ? new Edge(src, dst) : new Edge(dst, src);
  }

  @Override
  public boolean write(String filename, String key) {
    Filewriter fw = new Filewriter(filename);

    this.writeHeader(fw, this.getClass(), key);

    Iterator<Entry<Edge, LinkWeight>> it = this.weights.entrySet().iterator();
    while (it.hasNext()) {
      Entry<Edge, LinkWeight> entry = it.next();
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
    this.weights = new HashMap<Edge, LinkWeight>();
    String line = null;
    while ((line = fr.readLine()) != null) {
      String[] parts = line.split(" ");
      if (parts.length < 2) continue;
      Edge e = new Edge(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
      double val_low = Double.parseDouble(parts[2]);
      double val = Double.parseDouble(parts[3]);
      double val_high = Double.parseDouble(parts[4]);
      this.weights.put(e, new LinkWeight(val_low, val_high, val));
    }

    fr.close();

    return key;
  }
}
