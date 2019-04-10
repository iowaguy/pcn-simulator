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
 * BreadthFirstSearch.java
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
import java.util.Collections;
import java.util.LinkedList;
import java.util.Queue;

import gtna.graph.Graph;
import gtna.routing.table.RoutingTables;

/**
 * @author benni
 */
public class BreadthFirstSearch extends ShortestPathsAlgorithm {

  @Override
  public int[][] getShortestPaths(Graph graph, int start) {
    int[] dist = new int[graph.getNodeCount()];
    int[] previous = new int[graph.getNodeCount()];
    int[] nextHop = new int[graph.getNodeCount()];
    boolean[] seen = new boolean[graph.getNodeCount()];
    Queue<Integer> queue = new LinkedList<Integer>();

    dist[start] = 0;
    previous[start] = RoutingTables.noRoute;
    nextHop[start] = RoutingTables.noRoute;
    seen[start] = true;
    queue.add(start);

    while (!queue.isEmpty()) {
      int current = queue.poll();
      for (int out : graph.getNode(current).getOutgoingEdges()) {
        if (seen[out]) {
          continue;
        }
        dist[out] = dist[current] + 1;
        previous[out] = current;
        if (current == start) {
          nextHop[out] = out;
        } else {
          nextHop[out] = nextHop[current];
        }
        seen[out] = true;
        queue.add(out);
      }
    }

    return new int[][]{dist, previous, nextHop};
  }

  @Override
  public ArrayList<Integer> getShortestPathForSrcDest(Graph graph, int start, int dest) {
    ArrayList<Integer> respath = new ArrayList<Integer>();
    int[][] pre = new int[graph.getNodeCount()][2];
    for (int i = 0; i < pre.length; i++)
      pre[i][0] = -1;

    Queue<Integer> q = new LinkedList<Integer>();
    q.add(start);

    pre[start][0] = -2;

    boolean found = false;
    while (!found && !q.isEmpty()) {
      int n1 = q.poll();
      int[] out = graph.getNode(n1).getOutgoingEdges();
      for (int n : out) {
        if (pre[n][0] == -1) { // first visit
          pre[n][0] = n1; // previous
          pre[n][1] = pre[n1][1] + 1; //distance

          if (n == dest) {
            //respath = new Integer[pre[n][1] + 1];
            while (n != -2) {
              //respath[pre[n][1]] = n;
              respath.add(n);
              n = pre[n][0];
            }
            found = true;
          }
          q.add(n);
        }
      }
    }

    if (found) {
      Collections.reverse(respath);
      return respath;
    } else
      return null;

  }
}
