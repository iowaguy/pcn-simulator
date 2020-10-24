package treeembedding.credit.partioner;

import java.util.HashSet;

import gtna.graph.Graph;

public abstract class Partitioner {
  String name;

  public Partitioner(String name) {
    this.name = name;
  }

  public String getName() {
    return name;
  }


  public abstract double[] partition(Graph g, int src, int dst, double val, int trees);


  public double[] partition(Graph g, int src, int dst, double val,
                            double[] pathMaximums) {
    //develop val randomly, redistribute parts that exceed min
    double remainder = val; //value still to be assigned
    HashSet<Integer> saturated = new HashSet<Integer>(); //routes that are used at full capacity
    double[] res = this.partition(g, src, dst, val, pathMaximums.length);
    if (res == null) return null;
    while (remainder > 0) {
      remainder = 0;
      for (int i = 0; i < res.length; i++) {
        if (res[i] > pathMaximums[i]) {
          remainder = remainder + (res[i] - pathMaximums[i]);
          res[i] = pathMaximums[i];
          saturated.add(i);
        }
      }

      if (saturated.size() == pathMaximums.length) {
        //mins cannot be satisfied, transaction fails
        return null;
      } else {
        if (remainder > 0) {
          //distribute remainder on other routes that still have capacity
          double[] adds = this.partition(g, src, dst, remainder, pathMaximums.length - saturated.size());
          int k = 0;
          for (double add : adds) {
            while (saturated.contains(k)) {
              k++;
            }
            res[k] = res[k] + add;
            k++;
          }
        }
      }
    }

    return res;
  }
}
