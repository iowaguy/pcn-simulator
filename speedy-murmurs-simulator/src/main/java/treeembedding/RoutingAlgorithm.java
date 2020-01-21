package treeembedding;

import treeembedding.treerouting.Treeroute;
import treeembedding.treerouting.TreerouteOnly;
import treeembedding.treerouting.TreerouteSilentW;
import treeembedding.treerouting.TreerouteTDRAP;

public enum RoutingAlgorithm {
  SILENTWHISPERS(0, "SW", true, false, new TreerouteSilentW(), Collateralization.NONE),
  SILENTWHISPERS_NO_MPC(1, "SW_NO_MPC", false, false, new TreerouteSilentW(), Collateralization.NONE),
  SILENTWHISPERS_DYN(2, "SW_DYN", true, true, new TreerouteSilentW(), Collateralization.NONE),
  SILENTWHISPERS_DYN_NO_MPC(3, "SW_DYN_NO_MPC", false, true, new TreerouteSilentW(), Collateralization.NONE),


  VOUTE_MPC_NO_DYN(4, "V_MPC_NO_DYN", true, false, new TreerouteTDRAP(), Collateralization.STRICT),
  VOUTE_NO_DYN(5, "V_NO_DYN", false, false, new TreerouteTDRAP(), Collateralization.STRICT),
  VOUTE_MPC(6, "V_MPC", true, true, new TreerouteTDRAP(), Collateralization.STRICT),
  SPEEDYMURMURS(7, "SM", false, true, new TreerouteTDRAP(), Collateralization.STRICT),
  SPEEDYMURMURS_NO_COLLATERALIZATION(7, "SM_NO_COLL", false, true, new TreerouteTDRAP(), Collateralization.NONE),
  SPEEDYMURMURS_TOTAL_COLLATERALIZATION(7, "SM_TOT_COL", false, true, new TreerouteTDRAP(), Collateralization.TOTAL),
  TREE_ONLY_MCP_NO_DYN(8, "TO_MPC_NO_DYN", true, false, new TreerouteOnly(), Collateralization.STRICT),
  TREE_ONLY_DYN_NO_MCP(9, "TO_DYN_NO_MPC", false, true, new TreerouteOnly(), Collateralization.STRICT),

  MAXFLOW(10, "M", false, false, null, Collateralization.NONE),
  MAXFLOW_COLLATERALIZE(10, "MC", false, false, null, Collateralization.STRICT),
  MAXFLOW_TOTAL_COLLATERALIZE(10, "MTC", false, false, null, Collateralization.TOTAL);

  private int id;
  private String shortName;
  private boolean usesMPC; // uses multi-party computation to determine minimum or do routing adhoc
  private boolean doesDynamicRepair; // true if topology changes are immediately fixed rather than recomputation each epoch
  private Treeroute treeroute;
  private Collateralization collateralization;

  public enum Collateralization {
    NONE,   // no funds are collateralized
    STRICT, // each transaction only collateralizes the exact amount of the transaction for any link it uses
    TOTAL   // total value of funds on link are collateralized for each transaction
  }

  RoutingAlgorithm(int id, String shortName, boolean usesMPC, boolean doesDynamicRepair,
                   Treeroute treeroute, Collateralization collateralization) {
    this.id = id;
    this.shortName = shortName;
    this.usesMPC = usesMPC;
    this.doesDynamicRepair = doesDynamicRepair;
    this.treeroute = treeroute;
    this.collateralization = collateralization;
  }

  public Collateralization collateralizationType() {
    return this.collateralization;
  }

  public int getId() {
    return this.id;
  }

  public String getShortName() {
    return this.shortName;
  }

  public boolean usesMPC() {
    return usesMPC;
  }

  public boolean doesDynamicRepair() {
    return doesDynamicRepair;
  }

  public Treeroute getTreeroute() {
    return treeroute;
  }

  @Override
  public String toString() {
    return shortName;
  }
}
