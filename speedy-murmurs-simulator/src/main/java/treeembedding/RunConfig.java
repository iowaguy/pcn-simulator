package treeembedding;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import treeembedding.byzantine.Attack;
import treeembedding.byzantine.AttackType;
import treeembedding.byzantine.AttackerSelection;

@JsonIgnoreProperties(ignoreUnknown = true)
public class RunConfig {
  @JsonProperty("base")
  private String basePath;

  @JsonProperty("topology")
  private String topologyPath;

  @JsonProperty("transaction_set")
  private String transactionPath;

  @JsonProperty("simulation_type")
  private SimulationTypes simulationType;

  @JsonProperty("force_overwrite")
  private boolean forceOverwrite;

  @JsonProperty("routing_algorithm")
  private RoutingAlgorithm routingAlgorithm;

  @JsonProperty("attempts")
  private int attempts;

  @JsonProperty("trees")
  private int trees;

  @JsonProperty("attack_properties")
  private Attack attackProperties;

  @JsonProperty("iterations")
  private int iterations;

  @JsonProperty("step")
  private int step;

  @JsonProperty("concurrent_transactions")
  private boolean concurrentTransactions;

  @JsonProperty("network_latency_ms")
  private int networkLatencyMs;

  @JsonProperty("concurrent_transactions_count")
  private int concurrentTransactionsCount;

  @JsonProperty("new_links_path")
  private String newLinksPath;

  @JsonProperty("log_level")
  private String logLevel;

  @JsonProperty("epoch_length")
  private double epochLength;

  // useful for controlling the order of transactions in tests
  private int transactionDelayMs;

  public double getEpochLength() {
    if (epochLength == 0) {
      return 165.55245497208898;
    } else {
      return epochLength;
    }
  }

  public int getTransactionDelayMs() {
    return transactionDelayMs;
  }

  public void setTransactionDelayMs(int transactionDelayMs) {
    this.transactionDelayMs = transactionDelayMs;
  }

  public String getLogLevel() {
    if (logLevel == null) {
      return "INFO";
    } else {
      return logLevel;
    }
  }

  public void setLogLevel(String logLevel) {
    this.logLevel = logLevel;
  }

  public void setConcurrentTransactions(boolean concurrentTransactions) {
    this.concurrentTransactions = concurrentTransactions;
  }

  public String getNewLinksPath() {
    return newLinksPath;
  }

  public void setNewLinksPath(String newLinksPath) {
    this.newLinksPath = newLinksPath;
  }

  public int getConcurrentTransactionsCount() {
    return concurrentTransactionsCount;
  }

  public void setConcurrentTransactionsCount(int concurrentTransactionsCount) {
    this.concurrentTransactionsCount = concurrentTransactionsCount;
  }

  public int getNetworkLatencyMs() {
    return networkLatencyMs;
  }

  public void setNetworkLatencyMs(int networkLatencyMs) {
    this.networkLatencyMs = networkLatencyMs;
  }

  public boolean areTransactionsConcurrent() {
    return concurrentTransactions;
  }

  public int getStep() {
    return step;
  }

  public void setStep(int step) {
    this.step = step;
  }

  public int getIterations() {
    return iterations;
  }

  public void setIterations(int iterations) {
    this.iterations = iterations;
  }

  public String getBasePath() {
    return basePath;
  }

  public void setBasePath(String basePath) {
    this.basePath = basePath;
  }

  public String getTopologyPath() {
    return topologyPath;
  }

  public void setTopologyPath(String topologyPath) {
    this.topologyPath = topologyPath;
  }

  public String getTransactionPath() {
    return transactionPath;
  }

  public void setTransactionPath(String transactionPath) {
    this.transactionPath = transactionPath;
  }

  public void setSimulationType(SimulationTypes simulationType) {
    this.simulationType = simulationType;
  }

  public boolean isForceOverwrite() {
    return forceOverwrite;
  }

  public void setForceOverwrite(boolean forceOverwrite) {
    this.forceOverwrite = forceOverwrite;
  }

  public RoutingAlgorithm getRoutingAlgorithm() {
    return routingAlgorithm;
  }

  public void setRoutingAlgorithm(RoutingAlgorithm routingAlgorithm) {
    this.routingAlgorithm = routingAlgorithm;
  }

  public int getAttempts() {
    return attempts;
  }

  public void setAttempts(int attempts) {
    this.attempts = attempts;
  }

  public int getTrees() {
    return trees;
  }

  public void setTrees(int trees) {
    this.trees = trees;
  }

  public Attack getAttackProperties() {
    if (attackProperties != null) {
      return attackProperties;
    } else {
      Attack a = new Attack();
      a.setSelection(AttackerSelection.NONE);
      a.setType(AttackType.NONE);
      a.setNumAttackers(0);
      return a;
    }
  }

  public void setAttackProperties(Attack attackProperties) {
    this.attackProperties = attackProperties;
  }
}