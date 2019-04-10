The files that start with RoutingAlg all extend `RoutingAlgorithm` and represent refactored code from prior algorithms as well as new algorithms
These algorithms work with an event driven model - controlled from `PaymentNetwork.java` where events are of type `RoutingEvent`

Every routing algorithm extends the abstract class `RoutingAlgorithm` and in particular implement the function 
route `public double route(Transaction cur, Graph g, boolean[] exclude, boolean isPacket, double curTime)` that 
returns how much transaction amount was completed when the `Transaction cur` was completed. 
If `isPacket` is set to true, we allow partial completions, otherwise we reset the state of the Payment Channel to what it was
before the transaction was attempted and treat it as a failure.

The files with Credit* are all old files and can be neglected for now. The partitioner folder allows us to control how to partition path choices for a transaction. It is only relevant for SpeedyMurmurs and SilentWhispers.
