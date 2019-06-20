package treeembedding.credit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import treeembedding.RoutingAlgorithm;
import treeembedding.RunConfig;
import treeembedding.SimulationTypes;

class CreditNetworkTest {
  private RunConfig rc;

  @BeforeEach
  void setup() {
    rc = new RunConfig();
    rc.setAttempts(1);
    rc.setBasePath("../test-data/finalSets/dynamic");
    rc.setForceOverwrite(true);
    rc.setIterations(1);
    rc.setSimulationType(SimulationTypes.DYNAMIC);
    rc.setStep(0);
    rc.setTrees(3);
    rc.setConcurrentTransactions(true);
    rc.setConcurrentTransactionsCount(50);
    rc.setNetworkLatencyMs(171);
  }

  @Test
  void pathLinkUpdate() {
    rc.setRoutingAlgorithm(RoutingAlgorithm.SILENTWHISPERS);
    rc.setTopologyPath("jan2013-lcc-t0.graph");
    rc.setTransactionPath("jan2013-trans-lcc-noself-uniq-1.txt");
    rc.setNewLinksPath("jan2013-newlinks-lcc-sorted-uniq-t0.txt");
  }
}