package treeembedding.credit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import gtna.data.Series;
import gtna.metrics.Metric;
import gtna.networks.Network;
import gtna.networks.util.ReadableFile;
import gtna.util.Config;
import treeembedding.RoutingAlgorithm;
import treeembedding.RunConfig;
import treeembedding.SimulationTypes;
import treeembedding.byzantine.Attack;
import treeembedding.byzantine.AttackType;
import treeembedding.byzantine.AttackerSelection;
import treeembedding.credit.partioner.Partitioner;
import treeembedding.credit.partioner.RandomPartitioner;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CreditNetworkTest {
  private static final String TEST_DATA_BASE = "../test-data/finalSets/dynamic";
  private RunConfig runConfig;
  private static final int EPOCH = 100;
  private static final double ACCEPTABLE_ERROR = 0.0001;

  @BeforeEach
  void setup() {
    runConfig = new RunConfig();
    runConfig.setAttempts(1);

    runConfig.setForceOverwrite(true);
    runConfig.setIterations(1);
    runConfig.setSimulationType(SimulationTypes.DYNAMIC);
    runConfig.setStep(0);
    runConfig.setTrees(2);
    runConfig.setConcurrentTransactions(true);
    runConfig.setConcurrentTransactionsCount(50);
    runConfig.setNetworkLatencyMs(171);
    runConfig.setRunDirPath(TEST_DATA_BASE + "/output-data");
    runConfig.setLogLevel("DEBUG");
    Config.overwrite("SKIP_EXISTING_DATA_FOLDERS", Boolean.toString(false));
  }

  CreditLinks singlePathLinkUpdate(RoutingAlgorithm ra, String testDir) {
    return singlePathLinkUpdate(ra, testDir, null);
  }

  CreditLinks singlePathLinkUpdate(RoutingAlgorithm ra, String testDir, Attack attack) {
    runConfig.setBasePath(TEST_DATA_BASE + testDir);
    runConfig.setRoutingAlgorithm(ra);
    runConfig.setTopologyPath("topology.graph");
    runConfig.setTransactionPath("transactions.txt");
    runConfig.setNewLinksPath("newlinks.txt");

    if (attack != null) {
      runConfig.setAttackProperties(attack);
    }

    RoutingAlgorithm routingAlgorithm = runConfig.getRoutingAlgorithm();
    String trans = runConfig.getBasePath() + "/" + runConfig.getTransactionPath();
    String newlinks = runConfig.getBasePath() + "/" + runConfig.getNewLinksPath();
    int step = runConfig.getStep();
    String graph = runConfig.getBasePath() + "/" + runConfig.getTopologyPath();
    String name = routingAlgorithm.getShortName() + "-P" + (step + 1);
    double epoch = EPOCH * 1000;
    int max = 1;
    double req = EPOCH * 2;
    int[] roots = {2, 3};
    Partitioner part = new RandomPartitioner();

    CreditNetwork creditNetwork = new CreditNetwork(trans, name, epoch, routingAlgorithm, req, part, roots, max, newlinks, runConfig);

    Network net = new ReadableFile(name, name, graph, null);
    Series.generate(net, new Metric[]{creditNetwork}, 0, 0);
    return creditNetwork.getCreditLinks();
  }

  @Test
  void singlePathLinkUpdateSilentWhispers() {
    String testDir = "/single-transaction-test";
    CreditLinks edgeweights = singlePathLinkUpdate(RoutingAlgorithm.SILENTWHISPERS, testDir);
    assertEquals(90.0, edgeweights.getWeight(0, 2), ACCEPTABLE_ERROR);
    assertEquals(90.0, edgeweights.getWeight(2, 3), ACCEPTABLE_ERROR);
    assertEquals(90.0, edgeweights.getWeight(3, 5), ACCEPTABLE_ERROR);
    assertEquals(100.0, edgeweights.getWeight(3, 4), ACCEPTABLE_ERROR);
  }

  @Test
  void singlePathLinkUpdateSpeedyMurmurs() {
    String testDir = "/single-transaction-test";
    CreditLinks edgeweights = singlePathLinkUpdate(RoutingAlgorithm.SPEEDYMURMURS, testDir);
    assertEquals(90.0, edgeweights.getWeight(0, 2), ACCEPTABLE_ERROR);
    assertEquals(90.0, edgeweights.getWeight(2, 3), ACCEPTABLE_ERROR);
    assertEquals(90.0, edgeweights.getWeight(3, 5), ACCEPTABLE_ERROR);
    assertEquals(100.0, edgeweights.getWeight(3, 4), ACCEPTABLE_ERROR);
  }

  @Test
  void singlePathConcurrentSilentWhispers() {
    String testDir = "/concurrent-transactions-test";
    CreditLinks edgeweights = singlePathLinkUpdate(RoutingAlgorithm.SILENTWHISPERS, testDir);
    assertEquals(70.0, edgeweights.getWeight(0, 2), ACCEPTABLE_ERROR);
    assertEquals(70.0, edgeweights.getWeight(2, 3), ACCEPTABLE_ERROR);
    assertEquals(70.0, edgeweights.getWeight(3, 5), ACCEPTABLE_ERROR);
    assertEquals(100.0, edgeweights.getWeight(3, 4), ACCEPTABLE_ERROR);
  }

  @Test
  void singlePathConcurrentSpeedyMurmurs() {
    String testDir = "/concurrent-transactions-test";
    CreditLinks edgeweights = singlePathLinkUpdate(RoutingAlgorithm.SPEEDYMURMURS, testDir);
    assertEquals(70.0, edgeweights.getWeight(0, 2), ACCEPTABLE_ERROR);
    assertEquals(70.0, edgeweights.getWeight(2, 3), ACCEPTABLE_ERROR);
    assertEquals(70.0, edgeweights.getWeight(3, 5), ACCEPTABLE_ERROR);
    assertEquals(100.0, edgeweights.getWeight(3, 4), ACCEPTABLE_ERROR);
  }

  // many concurrent transactions
  @Test
  void singlePathManyConcurrentSilentWhispers() {
    String testDir = "/many-concurrent-transactions-test";
    CreditLinks edgeweights = singlePathLinkUpdate(RoutingAlgorithm.SILENTWHISPERS, testDir);
    assertEquals(24.0, edgeweights.getWeight(0, 2), ACCEPTABLE_ERROR);
    assertEquals(24.0, edgeweights.getWeight(2, 3), ACCEPTABLE_ERROR);
    assertEquals(24.0, edgeweights.getWeight(3, 5), ACCEPTABLE_ERROR);
    assertEquals(100.0, edgeweights.getWeight(3, 4), ACCEPTABLE_ERROR);
  }

  // TODO fix test. it is failing because sometimes speedymurmurs is not finding a path, even though
  // it should always find a path in this topology
  @Test
  void singlePathManyConcurrentSpeedyMurmurs() {
    String testDir = "/many-concurrent-transactions-test";
    CreditLinks edgeweights = singlePathLinkUpdate(RoutingAlgorithm.SPEEDYMURMURS, testDir);
    assertEquals(24.0, edgeweights.getWeight(0, 2), ACCEPTABLE_ERROR);
    assertEquals(24.0, edgeweights.getWeight(2, 3), ACCEPTABLE_ERROR);
    assertEquals(24.0, edgeweights.getWeight(3, 5), ACCEPTABLE_ERROR);
    assertEquals(100.0, edgeweights.getWeight(3, 4), ACCEPTABLE_ERROR);
  }

  // multiple paths that are partially disjoint
  @Test
  void multiplePartiallyDisjointPathsSilentWhispers() {
    String testDir = "/partially-disjoint-concurrent-transactions-test";
    CreditLinks edgeweights = singlePathLinkUpdate(RoutingAlgorithm.SILENTWHISPERS, testDir);
    assertEquals(90.0, edgeweights.getWeight(0, 2), ACCEPTABLE_ERROR);
    assertEquals(70.0, edgeweights.getWeight(2, 3), ACCEPTABLE_ERROR);
    assertEquals(90.0, edgeweights.getWeight(3, 5), ACCEPTABLE_ERROR);
    assertEquals(80.0, edgeweights.getWeight(1, 2), ACCEPTABLE_ERROR);
    assertEquals(80.0, edgeweights.getWeight(3, 4), ACCEPTABLE_ERROR);
  }

  @Test
  void multiplePartiallyDisjointPathsSpeedyMurmurs() {
    String testDir = "/partially-disjoint-concurrent-transactions-test";
    CreditLinks edgeweights = singlePathLinkUpdate(RoutingAlgorithm.SPEEDYMURMURS, testDir);
    assertEquals(90.0, edgeweights.getWeight(0, 2), ACCEPTABLE_ERROR);
    assertEquals(70.0, edgeweights.getWeight(2, 3), ACCEPTABLE_ERROR);
    assertEquals(90.0, edgeweights.getWeight(3, 5), ACCEPTABLE_ERROR);
    assertEquals(80.0, edgeweights.getWeight(1, 2), ACCEPTABLE_ERROR);
    assertEquals(80.0, edgeweights.getWeight(3, 4), ACCEPTABLE_ERROR);
  }


  // transactions in opposite directions
  @Test
  void oppositeDirectionsConcurrentSilentWhispers() {
    String testDir = "/opposite-directions-concurrent-transactions-test";
    CreditLinks edgeweights = singlePathLinkUpdate(RoutingAlgorithm.SILENTWHISPERS, testDir);
    assertEquals(110.0, edgeweights.getWeight(0, 2), ACCEPTABLE_ERROR);
    assertEquals(110.0, edgeweights.getWeight(2, 3), ACCEPTABLE_ERROR);
    assertEquals(110.0, edgeweights.getWeight(3, 5), ACCEPTABLE_ERROR);
    assertEquals(100.0, edgeweights.getWeight(3, 4), ACCEPTABLE_ERROR);
  }

  @Test
  void oppositeDirectionsConcurrentSpeedyMurmurs() {
    String testDir = "/opposite-directions-concurrent-transactions-test";
    CreditLinks edgeweights = singlePathLinkUpdate(RoutingAlgorithm.SPEEDYMURMURS, testDir);
    assertEquals(110.0, edgeweights.getWeight(0, 2), ACCEPTABLE_ERROR);
    assertEquals(110.0, edgeweights.getWeight(2, 3), ACCEPTABLE_ERROR);
    assertEquals(110.0, edgeweights.getWeight(3, 5), ACCEPTABLE_ERROR);
    assertEquals(100.0, edgeweights.getWeight(3, 4), ACCEPTABLE_ERROR);
  }

  /**
   * Single transaction should fail
   */
  @Test
  void singleTransactionWithGriefingSpeedyMurmurs() {
    String testDir = "/single-transaction-test";
    Attack attack = new Attack();
    attack.setNumAttackers(1);
    attack.setReceiverDelayMs(2000);
    attack.setType(AttackType.GRIEFING);
    attack.setSelection(AttackerSelection.SELECTED);
    Set<Integer> selectedByzantineNodes = new HashSet<>();
    selectedByzantineNodes.add(5);
    attack.setSelectedByzantineNodes(selectedByzantineNodes);

    CreditLinks edgeweights = singlePathLinkUpdate(RoutingAlgorithm.SPEEDYMURMURS, testDir, attack);
    assertEquals(100.0, edgeweights.getWeight(0, 2), ACCEPTABLE_ERROR);
    assertEquals(100.0, edgeweights.getWeight(2, 3), ACCEPTABLE_ERROR);
    assertEquals(100.0, edgeweights.getWeight(3, 5), ACCEPTABLE_ERROR);
    assertEquals(100.0, edgeweights.getWeight(3, 4), ACCEPTABLE_ERROR);
  }

  /**
   * Both transactions should fail because one transaction will hold the funds so there is not
   * enough liquidity for the other to complete, then the first transaction will fail because the
   * attacker will not approve it.
   */
  @Test
  void concurrentTransactionsWithGriefingSpeedyMurmurs() {
    String testDir = "/partially-disjoint-concurrent-transactions-test";
    Attack attack = new Attack();
    attack.setNumAttackers(1);
    attack.setReceiverDelayMs(2000);
    attack.setType(AttackType.GRIEFING);
    attack.setSelection(AttackerSelection.SELECTED);
    Set<Integer> selectedByzantineNodes = new HashSet<>();
    selectedByzantineNodes.add(5);
    attack.setSelectedByzantineNodes(selectedByzantineNodes);

    CreditLinks edgeweights = singlePathLinkUpdate(RoutingAlgorithm.SPEEDYMURMURS, testDir, attack);
    assertEquals(100.0, edgeweights.getWeight(0, 2), ACCEPTABLE_ERROR);
    assertEquals(100.0, edgeweights.getWeight(2, 3), ACCEPTABLE_ERROR);
    assertEquals(100.0, edgeweights.getWeight(3, 5), ACCEPTABLE_ERROR);
    assertEquals(100.0, edgeweights.getWeight(3, 4), ACCEPTABLE_ERROR);
    assertEquals(100.0, edgeweights.getWeight(1, 2), ACCEPTABLE_ERROR);
  }

  /**
   * The non-griefed payment should still succeed
   */
  @Test
  void concurrentTransactionsWithGriefingSilentWhispers() {
    String testDir = "/partially-disjoint-concurrent-transactions-test";
    Attack attack = new Attack();
    attack.setNumAttackers(1);
    attack.setReceiverDelayMs(2000);
    attack.setType(AttackType.GRIEFING);
    attack.setSelection(AttackerSelection.SELECTED);
    Set<Integer> selectedByzantineNodes = new HashSet<>();
    selectedByzantineNodes.add(5);
    attack.setSelectedByzantineNodes(selectedByzantineNodes);

    CreditLinks edgeweights = singlePathLinkUpdate(RoutingAlgorithm.SPEEDYMURMURS, testDir, attack);
    assertEquals(100.0, edgeweights.getWeight(0, 2), ACCEPTABLE_ERROR);
    assertEquals(80.0, edgeweights.getWeight(2, 3), ACCEPTABLE_ERROR);
    assertEquals(100.0, edgeweights.getWeight(3, 5), ACCEPTABLE_ERROR);
    assertEquals(80.0, edgeweights.getWeight(3, 4), ACCEPTABLE_ERROR);
    assertEquals(80.0, edgeweights.getWeight(1, 2), ACCEPTABLE_ERROR);
  }

  /*
  The goal with these is to test out a transaction that goes along two (at least partially) disjoint
  paths. Couldn't get it to work, because I couldn't find a way to force the tree creation to use
  disjoint paths deterministically.
*/
  /*@Test
  void multiPathLinkUpdateSilentWhispers() {
    String testDir = "/multipath-single-transaction-test";
    CreditLinks edgeweights = singlePathLinkUpdate(RoutingAlgorithm.SILENTWHISPERS, testDir);
    assertEquals(0.0, edgeweights.getWeight(0, 2), ACCEPTABLE_ERROR);
    assertEquals(0.0, edgeweights.getWeight(2, 3), ACCEPTABLE_ERROR);
    assertEquals(0.0, edgeweights.getWeight(3, 5), ACCEPTABLE_ERROR);
    assertEquals(0.0, edgeweights.getWeight(0, 1), ACCEPTABLE_ERROR);
    assertEquals(0.0, edgeweights.getWeight(1, 2), ACCEPTABLE_ERROR);
    assertEquals(100.0, edgeweights.getWeight(3, 4), ACCEPTABLE_ERROR);
  }

  @Test
  void multiPathLinkUpdateSpeedyMurmurs() {
    String testDir = "/multipath-single-transaction-test";
    CreditLinks edgeweights = singlePathLinkUpdate(RoutingAlgorithm.SPEEDYMURMURS, testDir);
    assertEquals(edgeweights.getWeight(0, 2), 0.0, ACCEPTABLE_ERROR);
    assertEquals(edgeweights.getWeight(2, 3), 0.0, ACCEPTABLE_ERROR);
    assertEquals(edgeweights.getWeight(3, 5), 0.0, ACCEPTABLE_ERROR);
    assertEquals(edgeweights.getWeight(0, 1), 0.0, ACCEPTABLE_ERROR);
    assertEquals(edgeweights.getWeight(1, 2), 0.0, ACCEPTABLE_ERROR);
    assertEquals(edgeweights.getWeight(3, 4), 100.0, ACCEPTABLE_ERROR);
  }*/
}