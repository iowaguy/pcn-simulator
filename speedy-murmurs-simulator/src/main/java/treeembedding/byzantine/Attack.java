package treeembedding.byzantine;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Set;
import java.util.Vector;

import gtna.graph.Node;
import treeembedding.credit.Transaction;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Attack {
  @JsonProperty("attackers")
  private int numAttackers;

  @JsonProperty("attacker_selection")
  private AttackerSelection selection;

  @JsonProperty("attack_type")
  private AttackType type;

  @JsonProperty("receiver_delay_ms")
  private int receiverDelayMs;

  @JsonProperty("receiver_delay_variability")
  private int receiverDelayVariability;

  @JsonProperty("selected_byzantine_nodes")
  private Set<Integer> selectedByzantineNodes;

  public int getReceiverDelayVariability() {
    return receiverDelayVariability;
  }

  public void setReceiverDelayVariability(int receiverDelayVariability) {
    this.receiverDelayVariability = receiverDelayVariability;
  }

  public Set<Integer> generateAttackers(Node[] allNodes, Vector<Transaction> transactions) {
    ByzantineNodeSelection byz = selection.getSelectionType();
    if (byz instanceof TopRecipientsTxsByzantineNodeSelection) {
      byz.setNumByzantineNodes(numAttackers);
    } else if (byz instanceof RandomByzantineNodeSelection) {
      byz.setNumByzantineNodes(numAttackers);
    } else if (byz instanceof SpecificByzantineNodeSelection) {
      byz.setNumByzantineNodes(numAttackers);
      byz.setSelectedByzantineNodes(selectedByzantineNodes);
    } else {
      byz = new NoByzantineNodeSelection();
    }

    return byz.conscript(allNodes, transactions);
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
