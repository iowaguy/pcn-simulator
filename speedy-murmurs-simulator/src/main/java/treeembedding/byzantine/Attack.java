package treeembedding.byzantine;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Attack {
  @JsonProperty("attackers")
  private int numAttackers;

  @JsonProperty("attacker_selection")
  private AttackerSelection selection;

  @JsonProperty("attack_type")
  private AttackType type;

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
