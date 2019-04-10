package treeembedding.credit;


public class RoutingEvent implements Comparable<RoutingEvent> {
  private RoutingEventType eventType;

  double time;
  Transaction t;

  public RoutingEvent(Double time, RoutingEventType eventType, Transaction t) {
    this.time = time;
    this.eventType = eventType;
    this.t = t;
  }

  public Transaction getTxn() {
    return t;
  }

  public double getTime() {
    return time;
  }

  public RoutingEventType getType() {
    return eventType;
  }

  // order by time of event
  public int compareTo(RoutingEvent other) {
    return Double.compare(this.time, other.time);
  }

}
