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

  private Map<Edge, double[]> weights;


  public void setWeights(Map<Edge, double[]> weights) {
    this.weights = weights;
  }


  public CreditLinks() {
    this.weights = new HashMap<Edge, double[]>();
  }


  public double[] getWeights(int src, int dst) {
    if (src < dst) {
      return this.getWeights(new Edge(src, dst));
    } else {
      return this.getWeights(new Edge(dst, src));
    }
  }

  // update the amount inflight on this edge
  // if val is positive, you are queing a transaction on this edge
  // if val is negative, a transaction completed and you are releasing funds
  public void updateInflight(int src, int dst, double val) {
    Edge edge = src < dst ? new Edge(src, dst) : new Edge(dst, src);
    double[] ws = this.weights.get(edge);
    if (ws[3] + val < 0)
      ws[3] = 0;
    else
      ws[3] += val;
  }

  // remove the inflight amount from both sides
  public double getPot(int src, int dst) {
    if (src < dst) {
      double[] weight = this.getWeights(new Edge(src, dst));
      if (weight == null)
        System.out.println(src + " " + dst);
      return Math.max(weight[2] - weight[1] - weight[3], 0);
    } else {
      double[] weight = this.getWeights(new Edge(dst, src));
      if (weight == null)
        System.out.println(src + " " + dst);

      return Math.max(weight[1] - weight[0] - weight[3], 0);
    }
  }


  public Set<Entry<Edge, double[]>> getWeights() {
    return this.weights.entrySet();
  }

  public void setWeight(Edge edge, double[] weight) {
    this.weights.put(edge, weight);
  }

  // sets weight and adds the difference to inflight
  public boolean setWeight(int src, int dst, double sub) {
    updateInflight(src, dst, sub);
    if (src < dst) {
      double[] ws = this.weights.get(new Edge(src, dst));
      double dn = ws[1] + sub;
      if (dn <= ws[2]) {
        ws[1] = dn;
        return true;
      } else {
        return false;
      }
    } else {
      double[] ws = this.weights.get(new Edge(dst, src));
      double dn = ws[1] - sub;
      if (dn >= ws[0]) {
        ws[1] = dn;
        return true;
      } else {
        return false;
      }
    }
  }

  public void setBound(int src, int dst, double val) {
    if (src < dst) {
      double[] ws = this.weights.get(new Edge(src, dst));
      ws[2] = val;
    } else {
      double[] ws = this.weights.get(new Edge(dst, src));
      ws[0] = -val;
    }
  }

  // no update to inflight
  public void setWeight(Edge e, double val) {
    double[] ws = this.weights.get(e);
    ws[1] = val;
  }

  public void setInflight(Edge e, double val) {
    double[] ws = this.weights.get(e);
    ws[3] = val;
  }


  public double[] getWeights(Edge edge) {
    try {
      return this.weights.get(edge);
    } catch (NullPointerException e) {
      System.out.println(edge.getSrc() + " " + edge.getDst());
      return null;
    }
  }

  public double getWeight(int src, int dst) {
    Edge edge = src < dst ? new Edge(src, dst) : new Edge(dst, src);
    try {
      double[] ws = this.weights.get(edge);
      return ws[1];
    } catch (NullPointerException e) {
      return 0;
    }
  }

  public double getInflight(int src, int dst) {
    Edge edge = src < dst ? new Edge(src, dst) : new Edge(dst, src);
    try {
      double[] ws = this.weights.get(edge);
      return ws[3];
    } catch (NullPointerException e) {
      return 0;
    }
  }

  public double getWeight(Edge edge) {
    try {
      double[] ws = this.weights.get(edge);
      return ws[1];
    } catch (NullPointerException e) {
      return 0;
    }
  }

  public double getInflight(Edge edge) {
    try {
      double[] ws = this.weights.get(edge);
      return ws[3];
    } catch (NullPointerException e) {
      return 0;
    }
  }

  public double getTotalCredit() {
    double totalWeight = 0;
    for (Edge e : weights.keySet()) {
      totalWeight += getPot(e.getSrc(), e.getDst());
      totalWeight += getPot(e.getDst(), e.getSrc());
    }
    return totalWeight;
  }


  public Edge makeEdge(int src, int dst) {
    Edge edge = src < dst ? new Edge(src, dst) : new Edge(dst, src);
    return edge;
  }

  @Override
  public boolean write(String filename, String key) {
    Filewriter fw = new Filewriter(filename);

    this.writeHeader(fw, this.getClass(), key);

    Iterator<Entry<Edge, double[]>> it = this.weights.entrySet().iterator();
    while (it.hasNext()) {
      Entry<Edge, double[]> entry = it.next();
      double[] w = entry.getValue();
      String ws = w[0] + " " + w[1] + " " + w[2] + w[3];
      fw.writeln(entry.getKey().getSrc() + " " + entry.getKey().getDst() + " " + ws);
    }

    return fw.close();
  }

  @Override
  public String read(String filename) {
    Filereader fr = new Filereader(filename);

    String key = this.readHeader(fr);
    this.weights = new HashMap<Edge, double[]>();
    String line = null;
    while ((line = fr.readLine()) != null) {
      String[] parts = line.split(" ");
      if (parts.length < 2) continue;
      Edge e = new Edge(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
      double val_low = Double.parseDouble(parts[2]);
      double val = Double.parseDouble(parts[3]);
      double val_high = Double.parseDouble(parts[4]);
      double inflight = 0;
      double capacity = (val_high - val_low);
      val = 0;
      if (filename.contains("Ripple") || filename.contains("ripple"))
        capacity = 30000;
      val_high = capacity / 2.0;
      val_low = -1 * val_high;
      this.weights.put(e, new double[]{val_low, val, val_high, inflight});
    }

    fr.close();

    return key;
  }

}
