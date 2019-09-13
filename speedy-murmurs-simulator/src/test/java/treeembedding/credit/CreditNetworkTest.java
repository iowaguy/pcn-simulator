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
  private RoutingAlgorithm[] algos = new RoutingAlgorithm[]{RoutingAlgorithm.SILENTWHISPERS,
          RoutingAlgorithm.SPEEDYMURMURS, RoutingAlgorithm.MAXFLOW};

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
    runConfig.setNetworkLatencyMs(50);
    runConfig.setRunDirPath(TEST_DATA_BASE + "/output-data");
    runConfig.setLogLevel("ERROR");
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

    AbstractCreditNetworkBase m = null;
    if (ra == RoutingAlgorithm.MAXFLOW) {
      m = new CreditMaxFlow(trans, name, 0, 0, newlinks, epoch, runConfig);
    } else {
      m = new CreditNetwork(trans, name, epoch, routingAlgorithm, req, part, roots, max, newlinks, runConfig);

    }

    Network net = new ReadableFile(name, name, graph, null);
    Series.generate(net, new Metric[]{m}, 0, 0);
    return m.getCreditLinks();
  }

  @Test
  void singlePathLinkUpdate() {
    String testDir = "/single-transaction-test";
    for (RoutingAlgorithm ra : algos) {
      CreditLinks edgeweights = singlePathLinkUpdate(ra, testDir);
      assertEquals(90.0, edgeweights.getWeight(0, 2), ACCEPTABLE_ERROR);
      assertEquals(90.0, edgeweights.getWeight(2, 3), ACCEPTABLE_ERROR);
      assertEquals(90.0, edgeweights.getWeight(3, 5), ACCEPTABLE_ERROR);
      assertEquals(100.0, edgeweights.getWeight(3, 4), ACCEPTABLE_ERROR);
    }
  }

  @Test
  void singlePathConcurrent() {
    String testDir = "/concurrent-transactions-test";
    for (RoutingAlgorithm ra : algos) {
      CreditLinks edgeweights = singlePathLinkUpdate(ra, testDir);
      assertEquals(70.0, edgeweights.getWeight(0, 2), ACCEPTABLE_ERROR);
      assertEquals(70.0, edgeweights.getWeight(2, 3), ACCEPTABLE_ERROR);
      assertEquals(70.0, edgeweights.getWeight(3, 5), ACCEPTABLE_ERROR);
      assertEquals(100.0, edgeweights.getWeight(3, 4), ACCEPTABLE_ERROR);
    }
  }

  // many concurrent transactions
  @Test
  void singlePathManyConcurrentSilentWhispers() {
    String testDir = "/many-concurrent-transactions-test";
    CreditLinks edgeweights = singlePathLinkUpdate(RoutingAlgorithm.SILENTWHISPERS, testDir);
    assertEquals(24.0, edgeweights.getWeight(0, 2), ACCEPTABLE_ERROR);
    assertEquals(200.0, edgeweights.getWeights(0, 2).getUnlockedMax(), ACCEPTABLE_ERROR);
    assertEquals(0.0, edgeweights.getWeights(0, 2).getUnlockedMin(), ACCEPTABLE_ERROR);

    assertEquals(24.0, edgeweights.getWeight(2, 3), ACCEPTABLE_ERROR);
    assertEquals(200.0, edgeweights.getWeights(2, 3).getUnlockedMax(), ACCEPTABLE_ERROR);
    assertEquals(0.0, edgeweights.getWeights(2, 3).getUnlockedMin(), ACCEPTABLE_ERROR);

    assertEquals(24.0, edgeweights.getWeight(3, 5), ACCEPTABLE_ERROR);
    assertEquals(200.0, edgeweights.getWeights(3, 5).getUnlockedMax(), ACCEPTABLE_ERROR);
    assertEquals(0.0, edgeweights.getWeights(3, 5).getUnlockedMin(), ACCEPTABLE_ERROR);

    assertEquals(100.0, edgeweights.getWeight(3, 4), ACCEPTABLE_ERROR);
    assertEquals(200.0, edgeweights.getWeights(3, 4).getUnlockedMax(), ACCEPTABLE_ERROR);
    assertEquals(0.0, edgeweights.getWeights(3, 4).getUnlockedMin(), ACCEPTABLE_ERROR);

    assertEquals(100.0, edgeweights.getWeight(1, 2), ACCEPTABLE_ERROR);
    assertEquals(200.0, edgeweights.getWeights(1, 2).getUnlockedMax(), ACCEPTABLE_ERROR);
    assertEquals(0.0, edgeweights.getWeights(1, 2).getUnlockedMin(), ACCEPTABLE_ERROR);
  }

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
  void multiplePartiallyDisjointPaths() {
    String testDir = "/partially-disjoint-concurrent-transactions-test";
    for (RoutingAlgorithm ra : algos) {
      CreditLinks edgeweights = singlePathLinkUpdate(ra, testDir);
      assertEquals(90.0, edgeweights.getWeight(0, 2), ACCEPTABLE_ERROR);
      assertEquals(70.0, edgeweights.getWeight(2, 3), ACCEPTABLE_ERROR);
      assertEquals(90.0, edgeweights.getWeight(3, 5), ACCEPTABLE_ERROR);
      assertEquals(80.0, edgeweights.getWeight(1, 2), ACCEPTABLE_ERROR);
      assertEquals(80.0, edgeweights.getWeight(3, 4), ACCEPTABLE_ERROR);
    }
  }

  // transactions in opposite directions
  @Test
  void oppositeDirectionsConcurrent() {
    String testDir = "/opposite-directions-concurrent-transactions-test";
    for (RoutingAlgorithm ra : algos) {
      CreditLinks edgeweights = singlePathLinkUpdate(ra, testDir);
      assertEquals(110.0, edgeweights.getWeight(0, 2), ACCEPTABLE_ERROR);
      assertEquals(110.0, edgeweights.getWeight(2, 3), ACCEPTABLE_ERROR);
      assertEquals(110.0, edgeweights.getWeight(3, 5), ACCEPTABLE_ERROR);
      assertEquals(100.0, edgeweights.getWeight(3, 4), ACCEPTABLE_ERROR);
    }
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
    String testDir = "/partially-disjoint-concurrent-transactions-test-with-contention";
    Attack attack = new Attack();
    attack.setNumAttackers(1);
    attack.setReceiverDelayMs(2000);
    attack.setType(AttackType.GRIEFING);
    attack.setSelection(AttackerSelection.SELECTED);
    Set<Integer> selectedByzantineNodes = new HashSet<>();
    selectedByzantineNodes.add(5);
    attack.setSelectedByzantineNodes(selectedByzantineNodes);

    // this is to guarantee that the griefed transaction starts first
    runConfig.setTransactionDelayMs(500);

    CreditLinks edgeweights = singlePathLinkUpdate(RoutingAlgorithm.SPEEDYMURMURS, testDir, attack);
    assertEquals(100.0, edgeweights.getWeight(0, 2), ACCEPTABLE_ERROR);
    assertEquals(200.0, edgeweights.getWeights(0, 2).getUnlockedMax(), ACCEPTABLE_ERROR);
    assertEquals(0.0, edgeweights.getWeights(0, 2).getUnlockedMin(), ACCEPTABLE_ERROR);

    assertEquals(100.0, edgeweights.getWeight(2, 3), ACCEPTABLE_ERROR);
    assertEquals(200.0, edgeweights.getWeights(2, 3).getUnlockedMax(), ACCEPTABLE_ERROR);
    assertEquals(0.0, edgeweights.getWeights(2, 3).getUnlockedMin(), ACCEPTABLE_ERROR);

    assertEquals(100.0, edgeweights.getWeight(3, 5), ACCEPTABLE_ERROR);
    assertEquals(200.0, edgeweights.getWeights(3, 5).getUnlockedMax(), ACCEPTABLE_ERROR);
    assertEquals(0.0, edgeweights.getWeights(3, 5).getUnlockedMin(), ACCEPTABLE_ERROR);

    assertEquals(100.0, edgeweights.getWeight(3, 4), ACCEPTABLE_ERROR);
    assertEquals(200.0, edgeweights.getWeights(3, 4).getUnlockedMax(), ACCEPTABLE_ERROR);
    assertEquals(0.0, edgeweights.getWeights(3, 4).getUnlockedMin(), ACCEPTABLE_ERROR);

    assertEquals(100.0, edgeweights.getWeight(1, 2), ACCEPTABLE_ERROR);
    assertEquals(200.0, edgeweights.getWeights(1, 2).getUnlockedMax(), ACCEPTABLE_ERROR);
    assertEquals(0.0, edgeweights.getWeights(1, 2).getUnlockedMin(), ACCEPTABLE_ERROR);

  }

  /**
   * The non-griefed payment should still succeed
   */
  @Test
  void concurrentTransactionsWithGriefingSilentWhispers() {
    String testDir = "/partially-disjoint-concurrent-transactions-test-with-contention";
    Attack attack = new Attack();
    attack.setNumAttackers(1);
    attack.setReceiverDelayMs(2000);
    attack.setType(AttackType.GRIEFING);
    attack.setSelection(AttackerSelection.SELECTED);
    Set<Integer> selectedByzantineNodes = new HashSet<>();
    selectedByzantineNodes.add(5);
    attack.setSelectedByzantineNodes(selectedByzantineNodes);

    CreditLinks edgeweights = singlePathLinkUpdate(RoutingAlgorithm.SILENTWHISPERS, testDir, attack);
    assertEquals(100.0, edgeweights.getWeight(0, 2), ACCEPTABLE_ERROR);
    assertEquals(20.0, edgeweights.getWeight(2, 3), ACCEPTABLE_ERROR);
    assertEquals(100.0, edgeweights.getWeight(3, 5), ACCEPTABLE_ERROR);
    assertEquals(20.0, edgeweights.getWeight(3, 4), ACCEPTABLE_ERROR);
    assertEquals(20.0, edgeweights.getWeight(1, 2), ACCEPTABLE_ERROR);
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