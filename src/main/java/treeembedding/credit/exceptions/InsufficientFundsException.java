package treeembedding.credit.exceptions;

public class InsufficientFundsException extends Exception {
  private static final String MESSAGE = "Insufficient funds available on link";

  public InsufficientFundsException() {
    super(MESSAGE);
  }
}
