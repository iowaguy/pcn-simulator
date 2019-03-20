package gtna.routing.greedyVariations;

import gtna.graph.Graph;
import gtna.graph.Node;
import gtna.id.BigIntegerIdentifier;
import gtna.id.BigIntegerIdentifierSpace;
import gtna.id.BigIntegerPartition;
import gtna.id.DoubleIdentifier;
import gtna.id.DoubleIdentifierSpace;
import gtna.id.DoublePartition;
import gtna.id.Identifier;
import gtna.routing.Route;
import gtna.routing.RoutingAlgorithm;
import gtna.util.parameter.IntParameter;
import gtna.util.parameter.Parameter;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Random;

/**
 * GravityPressure Routing as described in 'Hyperbolic Embedding and Routing for
 * Dynamic Graphs' by Andrej Cvetkovski Mark Crovella
 * 
 * @author stefanieroos
 * 
 */

public class GravityPressureRouting extends RoutingAlgorithm {
	int ttl;
	DoubleIdentifierSpace idSpaceD;
	DoublePartition[] pD;
	BigIntegerIdentifierSpace idSpaceBI;
	BigIntegerPartition[] pBI;
	boolean mode;
	int[] counts;

	public GravityPressureRouting() {
		this(Integer.MAX_VALUE);
	}

	/**
	 * @param ttl
	 */
	public GravityPressureRouting(int ttl) {
		super("GRAVITY_PRESSURE",
				new Parameter[] { new IntParameter("TTL", ttl) });

	}

	@Override
	public Route routeToTarget(Graph graph, int start, Identifier target,
			Random rand) {
		this.setSets(graph.getNodes().length);
		if (this.idSpaceBI != null) {
			return this.routeBI(new ArrayList<Integer>(), start,
					(BigIntegerIdentifier) target, rand, graph.getNodes(),
					this.idSpaceBI.getMaxDistance());
		} else if (this.idSpaceD != null) {
			return this.routeD(new ArrayList<Integer>(), start,
					(DoubleIdentifier) target, rand, graph.getNodes(),
					this.idSpaceD.getMaxDistance());
		} else {
			return null;
		}
	}

	private Route routeBI(ArrayList<Integer> route, int current,
			BigIntegerIdentifier target, Random rand, Node[] nodes, BigInteger minDist) {
		route.add(current);
		if (this.isEndPoint(current, target)) {
			return new Route(route, false);
		}
		if (route.size() > this.ttl) {
			return new Route(route, false);
		}
		BigInteger[] next = this.getNextBI(current, target, rand, nodes,
				minDist);
		int minNode = next[0].intValue();
		BigInteger dist = next[1];
		if (minNode == -1) {
			return new Route(route, false);
		}
		return this.routeBI(route, minNode, target, rand, nodes, dist);
	}

	private Route routeD(ArrayList<Integer> route, int current,
			DoubleIdentifier target, Random rand, Node[] nodes, double minDist) {
		route.add(current);
		if (this.isEndPoint(current, target)) {
			return new Route(route, true);
		}
		if (route.size() > this.ttl) {
			return new Route(route, false);
		}
		double[] next = this.getNextD(current, target, rand, nodes, minDist);
		int minNode = (int) next[0];
		// double dist = next[1];
		if (minNode == -1) {
			return new Route(route, false);
		}
		return this.routeD(route, minNode, target, rand, nodes, minDist);
	}

	@Override
	public boolean applicable(Graph graph) {
		return graph.hasProperty("ID_SPACE_0")
				&& (graph.getProperty("ID_SPACE_0") instanceof DoubleIdentifierSpace);
	}

	/**
	 * returns the index of next node as well the minimal distance to target of
	 * all nodes seen up to that point
	 * 
	 * @param current
	 * @param target
	 * @param rand
	 * @param nodes
	 * @param minDist
	 * @return
	 */
	public double[] getNextD(int current, DoubleIdentifier target, Random rand,
			Node[] nodes, double minDist) {
		double currentDist = this.pD[current].distance(target);
		// check if in Pressure phase
		if (this.mode == false) {
			// change to Gravity mode if node offers an improvement
			if (currentDist < minDist) {
				this.mode = true;
			} else {
				// choose node closest to target among those with least visits
				int minVisits = Integer.MAX_VALUE;
				double min = Double.MAX_VALUE;
				int minNode = -1;
				for (int out : nodes[current].getOutgoingEdges()) {
					double dist = this.pD[out].distance(target);
					if (this.counts[out] < minVisits) {
						minNode = out;
						min = dist;
						minVisits = this.counts[out];
					} else {
						if (this.counts[out] == minVisits && dist < min) {
							minNode = out;
							min = dist;
						}
					}
				}
				this.counts[current]++;
				return new double[] { (double) minNode, minDist };
			}
		}
		if (this.mode) {
			// determine neighbor closest to target
			double min = Double.MAX_VALUE;
			int minNode = -1;
			for (int out : nodes[current].getOutgoingEdges()) {
				double dist = this.pD[out].distance(target);
				if (dist < min) {
					minNode = out;
					min = dist;
				}
			}
			if (min < minDist) {
				return new double[] { (double) minNode, min };
			} else {
				// change to pressure mode if node is farer away from
				// destination
				this.mode = false;
				return this.getNextD(current, target, rand, nodes, minDist);
			}
		}
		return null;
	}

	public BigInteger[] getNextBI(int current, BigIntegerIdentifier target,
			Random rand, Node[] nodes, BigInteger minDist) {
		return null;
	}

	public void setSets(int nr) {
		counts = new int[nr];
	}

}
