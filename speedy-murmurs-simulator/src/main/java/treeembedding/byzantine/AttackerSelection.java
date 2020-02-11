package treeembedding.byzantine;

public enum AttackerSelection {
  RANDOM(new RandomByzantineNodeSelection()),
  SELECTED(new SpecificByzantineNodeSelection()),
  TOP_RECIPIENTS_BY_TXS(new TopRecipientsTxsByzantineNodeSelection()),
  NONE(new NoByzantineNodeSelection());

  private ByzantineNodeSelection selectionType;

  AttackerSelection(ByzantineNodeSelection byz) {
    selectionType = byz;
  }

  public ByzantineNodeSelection getSelectionType() {
    return selectionType;
  }
}
