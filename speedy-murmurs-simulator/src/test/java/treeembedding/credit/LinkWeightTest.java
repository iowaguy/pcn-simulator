package treeembedding.credit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import gtna.graph.Edge;
import treeembedding.credit.exceptions.InsufficientFundsException;
import treeembedding.credit.exceptions.TransactionFailedException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class LinkWeightTest {
  private LinkWeight l1;
  private LinkWeight l2;

  @BeforeEach
  void setUp() {
    Edge e1 = new Edge(1, 2);
    l1 = new LinkWeight(e1, 0, 1000, 0);

    Edge e2 = new Edge(1, 3);
    l2 = new LinkWeight(e2, -1000, 2000, 0);

  }

  @Test
  void testGetMaxTransactionAmount() {
    assertEquals(0, l1.getMaxTransactionAmount(true, true));
    assertEquals(1000, l2.getMaxTransactionAmount(true, true));
    assertEquals(2000, l2.getMaxTransactionAmount(false, true));
  }

  @Test
  void testAreFundsAvailable() {
    assertFalse(l1.areFundsAvailable(500, true));
    assertFalse(l1.areFundsAvailable(-1001, true));
    assertTrue(l1.areFundsAvailable(-500, true));
    assertTrue(l1.areFundsAvailable(-1000, true));

    // lock up some funds and make sure calculations are still correct
    try {
      l1.prepareUpdateWeight(-500, true);
    } catch (InsufficientFundsException e) {
      fail("Prepare failed");
    }
    assertTrue(l1.areFundsAvailable(-500, true));
    assertFalse(l1.areFundsAvailable(-501, true));
    assertFalse(l1.areFundsAvailable(500, true));

    // try to prepare a second valid transaction
    try {
      l1.prepareUpdateWeight(-200, true);
    } catch (InsufficientFundsException e) {
      fail("Prepare failed");
    }

    assertTrue(l1.areFundsAvailable(-300, true));
    assertFalse(l1.areFundsAvailable(-301, true));
    assertFalse(l1.areFundsAvailable(500, true));

    // try to prepare a transaction that doesn't have enough funds
    assertThrows(InsufficientFundsException.class,
            () -> l1.prepareUpdateWeight(-500, true));

    // unlock funds and make sure they return to original state
    try {
      l1.finalizeUpdateWeight(-500, true);
    } catch (TransactionFailedException e) {
      fail("Finalize update failed");
    }

    assertTrue(l1.areFundsAvailable(-300, true));
    assertFalse(l1.areFundsAvailable(-301, true));
    assertTrue(l1.areFundsAvailable(500, true));
    assertFalse(l1.areFundsAvailable(501, true));

    try {
      l1.finalizeUpdateWeight(-200, true);
    } catch (TransactionFailedException e) {
      fail("Finalize update failed");
    }
    assertTrue(l1.areFundsAvailable(-300, true));
    assertFalse(l1.areFundsAvailable(-301, true));
    assertTrue(l1.areFundsAvailable(700, true));
    assertFalse(l1.areFundsAvailable(701, true));
  }

  @Test
  void prepareUpdateWeight() {
  }

  @Test
  void finalizeUpdateWeight() {
  }
}