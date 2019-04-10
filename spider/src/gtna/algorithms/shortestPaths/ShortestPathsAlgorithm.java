/* ===========================================================
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
 * ShortestPaths.java
 * ---------------------------------------
 * (C) Copyright 2009-2011, by Benjamin Schiller (P2P, TU Darmstadt)
 * and Contributors
 *
 * Original Author: benni;
 * Contributors:    -;
 *
 * Changes since 2011-05-17
 * ---------------------------------------
 *
 */
package gtna.algorithms.shortestPaths;

import java.util.ArrayList;
import java.util.HashMap;

import gtna.graph.Graph;

/**
 * @author benni
 */
public abstract class ShortestPathsAlgorithm {
  /**
   * @return {int[][] dist, int[][] previous, int[][] nextHop} for all nodes in $graph
   */
  public int[][][] getShortestPaths(Graph graph) {
    int[][][] sp = new int[3][graph.getNodeCount()][];
    for (int i = 0; i < sp.length; i++) {
      int[][] temp = this.getShortestPaths(graph, i);
      sp[0][i] = temp[0];
      sp[1][i] = temp[1];
      sp[2][i] = temp[2];
    }
    return sp;
  }

  /**
   * @return {int[] dist, int[] previous, int[] nextHop} for node $start
   */
  public abstract int[][] getShortestPaths(Graph graph, int start);

  /**
   * @return ArrayList<Integer> for path from start to dest
   */
  public abstract ArrayList<Integer> getShortestPathForSrcDest(Graph graph, int start, int dest);

  /**
   * @param k number of paths to find
   * @return {int[][] dist, int[][] previous, int[][]} for k paths for all nodes in $graph
   */
  public HashMap<String, ArrayList<ArrayList<Integer>>> getKShortestPaths(Graph graph, int K) {
    HashMap<String, ArrayList<ArrayList<Integer>>> ksp = new HashMap<String, ArrayList<ArrayList<Integer>>>();
    for (int i = 0; i < graph.getNodeCount(); i++)
      for (int j = 0; j < graph.getNodeCount(); j++) {
        if (i == j)
          continue;
        String srcDstKey = i + "_" + j;
        Graph curGraph = new Graph(graph);
        ArrayList<ArrayList<Integer>> setOfPaths = new ArrayList<ArrayList<Integer>>(K);

        for (int k = 0; k < K; k++) {

          ArrayList<Integer> thisPath = this.getShortestPathForSrcDest(curGraph, i, j);
          if (thisPath == null) {
            break;
          }
          setOfPaths.add(thisPath);

          // each index's nextHop entry represents the next node on the path to the source
          // so removing that link removes its connection to another node who has a path
          // if my nexthop is me, i have a direct path to the source, so remove that link
          int source = i;
          for (int d = 1; d < thisPath.size(); d++) {
            int dest = thisPath.get(d);
            curGraph.removeEdge(source, dest);
            source = dest;
          }
        }
        ksp.put(srcDstKey, setOfPaths);
      }
    return ksp;

  }
}
