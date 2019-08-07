package treeembedding;

import treeembedding.treerouting.Treeroute;
import treeembedding.treerouting.TreerouteOnly;
import treeembedding.treerouting.TreerouteSilentW;
import treeembedding.treerouting.TreerouteTDRAP;

public enum RoutingAlgorithm {
  SILENTWHISPERS(0, "SW", true, false, new TreerouteSilentW(), false),
  SILENTWHISPERS_NO_MPC(1, "SW_NO_MPC", false, false, new TreerouteSilentW(), false),
  SILENTWHISPERS_DYN(2, "SW_DYN", true, true, new TreerouteSilentW(), false),
  SILENTWHISPERS_DYN_NO_MPC(3, "SW_DYN_NO_MPC", false, true, new TreerouteSilentW(), false),


  VOUTE_MPC_NO_DYN(4, "V_MPC_NO_DYN", true, false, new TreerouteTDRAP(), true),
  VOUTE_NO_DYN(5, "V_NO_DYN", false, false, new TreerouteTDRAP(), true),
  VOUTE_MPC(6, "V_MPC", true, true, new TreerouteTDRAP(), true),
  SPEEDYMURMURS(7, "SM", false, true, new TreerouteTDRAP(), true),

  TREE_ONLY_MCP_NO_DYN(8, "TO_MPC_NO_DYN", true, false, new TreerouteOnly(), true),
  TREE_ONLY_DYN_NO_MCP(9, "TO_DYN_NO_MPC", false, true, new TreerouteOnly(), true),

  MAXFLOW(10, "M", false, false, null, false);

  private int id;
  private String shortName;
  private boolean usesMPC; // uses multi-party computation to determine minimum or do routing adhoc
  private boolean doesDynamicRepair; // true if topology changes are immediately fixed rather than recomputation each epoch
  private Treeroute treeroute;
  private boolean lockFunds;

  RoutingAlgorithm(int id, String shortName, boolean usesMPC, boolean doesDynamicRepair,
                   Treeroute treeroute, boolean lockFunds) {
    this.id = id;
    this.shortName = shortName;
    this.usesMPC = usesMPC;
    this.doesDynamicRepair = doesDynamicRepair;
    this.treeroute = treeroute;
    this.lockFunds = lockFunds;
  }

  public boolean isFundLockingEnabled() {
    return lockFunds;
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
}
