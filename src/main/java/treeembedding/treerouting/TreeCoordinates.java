package treeembedding.treerouting;

import gtna.graph.GraphProperty;
import gtna.io.Filereader;
import gtna.io.Filewriter;

public class TreeCoordinates extends GraphProperty {
  long[][] coords;

  public TreeCoordinates() {

  }

  public TreeCoordinates(long[][] coords) {
    this.coords = coords;
  }

  public long[] getCoord(int node) {
    return this.coords[node];
  }

  public void setCoord(int node, long[] coord) {
    this.coords[node] = coord;
  }

  @Override
  public boolean write(String filename, String key) {
    Filewriter fw = new Filewriter(filename);
    this.writeHeader(fw, this.getClass(), key);
    fw.writeln("TREE_SIZE: " + this.coords.length);
    for (int i = 0; i < coords.length; i++) {
      StringBuilder l = new StringBuilder(i + "");
      for (int j = 0; j < coords[i].length; j++) {
        l.append(" ").append(coords[i][j]);
      }
      fw.writeln(l.toString());
    }
    return fw.close();
  }

  @Override
  public String read(String filename) {
    Filereader fr = new Filereader(filename);
    String key = this.readHeader(fr);
    int treesize = Integer.parseInt(fr.readLine().split(": ")[1]);
    this.coords = new long[treesize][];
    for (int i = 0; i < coords.length; i++) {
      String[] parts = readString(fr).split(" ");
      coords[i] = new long[parts.length - 1];
      for (int j = 1; j < parts.length; j++) {
        //System.out.println("i: " + i + ", j: " + j + ", val: " + parts[j]);
        coords[i][j - 1] = Long.parseLong(parts[j]);
      }
    }
    return key;
  }

}
