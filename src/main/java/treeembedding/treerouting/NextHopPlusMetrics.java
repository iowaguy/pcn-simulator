package treeembedding.treerouting;

import java.util.List;

public class NextHopPlusMetrics {
  private int index = 0;
  private int blockedLinks;
  private List<Integer> nextHops = null;
  private int[] path = null;

  NextHopPlusMetrics(int index, int blockedLinks) {
    this.index = index;
    this.blockedLinks = blockedLinks;
  }

  NextHopPlusMetrics(List<Integer> l, int blockedLinks) {
    this.nextHops = l;
    this.blockedLinks = blockedLinks;
  }

  NextHopPlusMetrics(int[] path, int blockedLinks) {
    this.path = path;
    this.blockedLinks = blockedLinks;
  }

  public int[] getPath() {
    return path;
  }

  public int getIndex() {
    return index;
  }

  public int getBlockedLinks() {
    return blockedLinks;
  }

  List<Integer> getNextHops() {
    return nextHops;
  }
}