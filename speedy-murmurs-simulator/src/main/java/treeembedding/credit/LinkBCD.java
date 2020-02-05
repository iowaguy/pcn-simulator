package treeembedding.credit;

public class LinkBCD {
  private String name;
  private double previous;
  private double current;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public double getPrevious() {
    return previous;
  }

  void setPrevious(double previous) {
    this.previous = previous;
  }

  public double getCurrent() {
    return current;
  }

  public void setCurrent(double current) {
    this.current = current;
  }
}
