package treeembedding.byzantine;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Set;

import gtna.graph.Node;

public class Attack {
  @JsonProperty("attackers")
  private int numAttackers;

  @JsonProperty("attacker_selection")
  private AttackerSelection selection;

  @JsonProperty("attack_type")
  private AttackType type;

  @JsonProperty("receiver_delay_ms")
  private int receiverDelayMs;

  @JsonProperty("selected_byzantine_nodes")
  private Set<Integer> selectedByzantineNodes;

  public Set<Integer> generateAttackers(Node[] allNodes) {
    ByzantineNodeSelection byz = selection.getSelectionType();
    if (byz == null) {
      byz = new NoByzantineNodeSelection();
    }
    byz.setNumByzantineNodes(numAttackers);
    byz.setSelectedByzantineNodes(selectedByzantineNodes);
    return byz.conscript(allNodes);
  }

  public void setSelectedByzantineNodes(Set<Integer> selectedByzantineNodes) {
    this.selectedByzantineNodes = selectedByzantineNodes;
  }

  public int getReceiverDelayMs() {
    return receiverDelayMs;
  }

  public void setReceiverDelayMs(int receiverDelayMs) {
    this.receiverDelayMs = receiverDelayMs;
  }

  public int getNumAttackers() {
    return numAttackers;
  }

  public void setNumAttackers(int numAttackers) {
    this.numAttackers = numAttackers;
  }

  public AttackerSelection getSelection() {
    return selection;
  }

  public void setSelection(AttackerSelection selection) {
    this.selection = selection;
  }

  public AttackType getType() {
    return type;
  }

  public void setType(AttackType type) {
    this.type = type;
  }
}
