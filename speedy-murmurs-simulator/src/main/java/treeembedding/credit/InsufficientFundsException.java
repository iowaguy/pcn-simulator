package treeembedding.credit;

class InsufficientFundsException extends Exception {
  private static final String MESSAGE = "Insufficient funds available on link";

  InsufficientFundsException() {
    super(MESSAGE);
  }
}
