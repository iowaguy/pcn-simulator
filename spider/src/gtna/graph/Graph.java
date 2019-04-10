/*
 * ===========================================================
 * GTNA : Graph-Theoretic Network Analyzer
 * ===========================================================
 *
 * (C) Copyright 2009-2011, by Benjamin Schiller (P2P, TU Darmstadt)
 * and Contributors
 *
 * Project Info:  http://www.p2p.tu-darmstadt.de/research/gtna/
 *
 * GTNA is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * GTNA is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
 * ---------------------------------------
 * Graph.java
 * ---------------------------------------
 * (C) Copyright 2009-2011, by Benjamin Schiller (P2P, TU Darmstadt)
 * and Contributors
 *
 * Original Author: Benjamin Schiller;
 * Contributors:    -;
 *
 * Changes since 2011-05-17
 * ---------------------------------------
 */
package gtna.graph;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

public class Graph {

  /**
   * @param name name of the graph
   */
  public Graph(String name) {
    this.name = name;
  }

  public Graph(Graph another) {
    this.name = new String(another.getName() + "copy");
    this.nodes = new Node[another.getNodeCount()];
    for (Node n : another.getNodes()) {
      int[] incomingEdges = Arrays.copyOf(n.getIncomingEdges(), n.getInDegree());
      int[] outgoingEdges = Arrays.copyOf(n.getOutgoingEdges(), n.getOutDegree());
      this.nodes[n.getIndex()] = new Node(n.getIndex(), null, incomingEdges, outgoingEdges);
    }

    this.edges = new Edges(this.nodes, another.computeNumberOfEdges());
    for (Edge e : another.getEdges().getEdges()) {
      this.edges.add(e.getSrc(), e.getDst());
    }

    for (Node n : this.nodes)
      n.setGraph(this);

    this.properties = new HashMap<String, GraphProperty>(another.getProperties());
  }

  public Graph(String name, int nodes) {
    this.name = name;
    this.nodes = Node.init(nodes, this);
  }

  public String toString() {
    return this.name + " (" + this.nodes.length + ")";
  }

  /*
   * NAME
   */

  private String name;

  /**
   * @return the name
   */
  public String getName() {
    return this.name;
  }

  /**
   * @param name the name to set
   */
  public void setName(String name) {
    this.name = name;
  }

  /*
   * NODES
   */

  private Node[] nodes = new Node[0];

  /**
   * @return the nodes
   */
  public Node[] getNodes() {
    return this.nodes;
  }

  /**
   * @param nodes the nodes to set
   */
  public void setNodes(Node[] nodes) {
    this.nodes = nodes;
  }

  /**
   * @return the node with index nodeIndex
   */
  public Node getNode(int nodeIndex) {
    return this.nodes[nodeIndex];
  }

  public int getNodeCount() {
    return this.nodes.length;
  }

  /*
   * EDGES
   */

  private Edges edges = null;

  public int computeNumberOfEdges() {
    int E = 0;
    for (Node n : this.nodes) {
      E += n.getOutDegree();
    }
    return E;
  }

  public Edge[] generateEdges() {
    int E = 0;
    for (Node n : this.nodes) {
      E += n.getOutDegree();
    }
    Edge[] edges = new Edge[E];
    int index = 0;
    for (Node n : this.nodes) {
      for (int out : n.getOutgoingEdges()) {
        edges[index++] = new Edge(n.getIndex(), out);
      }
    }
    return edges;
  }

  public Edges getEdges() {
    if (this.edges != null) {
      return this.edges;
    }
    int E = 0;
    for (Node n : this.nodes) {
      E += n.getOutDegree();
    }
    this.edges = new Edges(this.nodes, E);
    for (Node n : this.nodes) {
      for (int out : n.getOutgoingEdges()) {
        this.edges.add(n.getIndex(), out);
      }
    }
    return this.edges;
  }

  public boolean removeEdge(int src, int dest) {
    if (this.edges.remove(src, dest) == false)
      return false;
    if (getNode(src).removeOut(dest) == false)
      return false;
    if (getNode(dest).removeIn(src) == false)
      return false;
    return true;
  }

  public boolean addEdge(int src, int dest) {
    if (this.edges.add(src, dest) == false)
      return false;
    getNode(src).addOut(dest);
    getNode(dest).addIn(src);
    return true;
  }

  /*
   * PROPERTIES
   */

  private HashMap<String, GraphProperty> properties = new HashMap<String, GraphProperty>();

  public void addProperty(String key, GraphProperty property) {
    this.properties.put(key, property);
  }

  public void removeProperty(String key) {
    this.properties.remove(key);
  }

  @SuppressWarnings("rawtypes")
  public boolean hasProperty(String key, Class type) {
    return this.hasProperty(key) && type.isInstance(this.getProperty(key));
  }

  public boolean hasProperty(String key) {
    return this.properties.containsKey(key);
  }

  public GraphProperty getProperty(String key) {
    return this.properties.get(key);
  }

  public GraphProperty[] getProperties(String type) {
    ArrayList<GraphProperty> list = new ArrayList<GraphProperty>();
    int index = 0;
    while (this.hasProperty(type + "_" + index)) {
      list.add(this.getProperty(type + "_" + index));
      index++;
    }
    GraphProperty[] array = new GraphProperty[list.size()];
    for (int i = 0; i < array.length; i++) {
      array[i] = (GraphProperty) list.get(i);
    }
    return array;
  }

  public String getNextKey(String type) {
    int index = 0;
    while (this.hasProperty(type + "_" + index)) {
      index++;
    }
    return type + "_" + index;
  }

  public HashMap<String, GraphProperty> getProperties() {
    return this.properties;
  }
}
