package treeembedding.byzantine;

public enum AttackerSelection {
  RANDOM(new RandomByzantineNodeSelection()),
  SELECTED(new SpecificByzantineNodeSelection()),
  NONE(new NoByzantineNodeSelection());

  private ByzantineNodeSelection selectionType;

  AttackerSelection(ByzantineNodeSelection byz) {
    selectionType = byz;
  }

  public ByzantineNodeSelection getSelectionType() {
    return selectionType;
  }
}
