package treeembedding.credit;

public class LinkStats {
  private String name;
  private double previousBCD;
  private double currentBCD;
  private double previousDeviation;
  private double currentDeviation;

  double getPreviousDeviation() {
    return previousDeviation;
  }

  void setPreviousDeviation(double previousDeviation) {
    this.previousDeviation = previousDeviation;
  }

  double getCurrentDeviation() {
    return currentDeviation;
  }

  void setCurrentDeviation(double currentDeviation) {
    this.currentDeviation = currentDeviation;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  double getPreviousBCD() {
    return previousBCD;
  }

  void setPreviousBCD(double previousBCD) {
    this.previousBCD = previousBCD;
  }

  double getCurrentBCD() {
    return currentBCD;
  }

  void setCurrentBCD(double currentBCD) {
    this.currentBCD = currentBCD;
  }
}
