package treeembedding.credit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import gtna.graph.Edge;
import treeembedding.RoutingAlgorithm;
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
    assertEquals(0, l1.getMaxTransactionAmount(true, RoutingAlgorithm.Collateralization.STRICT, null, 0));
    assertEquals(1000, l2.getMaxTransactionAmount(true, RoutingAlgorithm.Collateralization.STRICT, null, 0));
    assertEquals(2000, l2.getMaxTransactionAmount(false, RoutingAlgorithm.Collateralization.STRICT, null, 0));
  }

  @Test
  void testAreFundsAvailableStrictCollateral() {
    ///////////////////////// Test maxs
    assertFalse(l1.areFundsAvailable(500, RoutingAlgorithm.Collateralization.STRICT));
    assertFalse(l1.areFundsAvailable(-1001, RoutingAlgorithm.Collateralization.STRICT));
    assertTrue(l1.areFundsAvailable(-500, RoutingAlgorithm.Collateralization.STRICT));
    assertTrue(l1.areFundsAvailable(-1000, RoutingAlgorithm.Collateralization.STRICT));

    // lock up some funds and make sure calculations are still correct
    try {
      l1.prepareUpdateWeight(-500, RoutingAlgorithm.Collateralization.STRICT);
    } catch (InsufficientFundsException e) {
      fail("Prepare failed");
    }
    assertTrue(l1.areFundsAvailable(-500, RoutingAlgorithm.Collateralization.STRICT));
    assertFalse(l1.areFundsAvailable(-501, RoutingAlgorithm.Collateralization.STRICT));
    assertFalse(l1.areFundsAvailable(500, RoutingAlgorithm.Collateralization.STRICT));

    // try to prepare a second valid transaction
    try {
      l1.prepareUpdateWeight(-200, RoutingAlgorithm.Collateralization.STRICT);
    } catch (InsufficientFundsException e) {
      fail("Prepare failed");
    }

    assertTrue(l1.areFundsAvailable(-300, RoutingAlgorithm.Collateralization.STRICT));
    assertFalse(l1.areFundsAvailable(-301, RoutingAlgorithm.Collateralization.STRICT));
    assertFalse(l1.areFundsAvailable(500, RoutingAlgorithm.Collateralization.STRICT));

    // try to prepare a transaction that doesn't have enough funds
    assertThrows(InsufficientFundsException.class,
            () -> l1.prepareUpdateWeight(-500, RoutingAlgorithm.Collateralization.STRICT));

    // unlock funds and make sure they return to original state
    try {
      l1.finalizeUpdateWeight(-500, RoutingAlgorithm.Collateralization.STRICT);
    } catch (TransactionFailedException e) {
      fail("Finalize update failed");
    }

    assertTrue(l1.areFundsAvailable(-300, RoutingAlgorithm.Collateralization.STRICT));
    assertFalse(l1.areFundsAvailable(-301, RoutingAlgorithm.Collateralization.STRICT));
    assertTrue(l1.areFundsAvailable(500, RoutingAlgorithm.Collateralization.STRICT));
    assertFalse(l1.areFundsAvailable(501, RoutingAlgorithm.Collateralization.STRICT));

    try {
      l1.finalizeUpdateWeight(-200, RoutingAlgorithm.Collateralization.STRICT);
    } catch (TransactionFailedException e) {
      fail("Finalize update failed");
    }
    assertTrue(l1.areFundsAvailable(-300, RoutingAlgorithm.Collateralization.STRICT));
    assertFalse(l1.areFundsAvailable(-301, RoutingAlgorithm.Collateralization.STRICT));
    assertTrue(l1.areFundsAvailable(700, RoutingAlgorithm.Collateralization.STRICT));
    assertFalse(l1.areFundsAvailable(701, RoutingAlgorithm.Collateralization.STRICT));


    ///////////////////////// Test mins
    assertTrue(l2.areFundsAvailable(500, RoutingAlgorithm.Collateralization.STRICT));
    assertFalse(l2.areFundsAvailable(1001, RoutingAlgorithm.Collateralization.STRICT));
    assertTrue(l2.areFundsAvailable(-500, RoutingAlgorithm.Collateralization.STRICT));
    assertFalse(l2.areFundsAvailable(-2001, RoutingAlgorithm.Collateralization.STRICT));

    // lock up some funds and make sure calculations are still correct
    try {
      l2.prepareUpdateWeight(500, RoutingAlgorithm.Collateralization.STRICT);
    } catch (InsufficientFundsException e) {
      fail("Prepare failed");
    }
    assertTrue(l2.areFundsAvailable(500, RoutingAlgorithm.Collateralization.STRICT));
    assertFalse(l2.areFundsAvailable(501, RoutingAlgorithm.Collateralization.STRICT));

    // try to prepare a second valid transaction
    try {
      l2.prepareUpdateWeight(200, RoutingAlgorithm.Collateralization.STRICT);
    } catch (InsufficientFundsException e) {
      fail("Prepare failed");
    }

    assertTrue(l2.areFundsAvailable(300, RoutingAlgorithm.Collateralization.STRICT));
    assertFalse(l2.areFundsAvailable(301, RoutingAlgorithm.Collateralization.STRICT));

    // try to prepare a transaction that doesn't have enough funds
    assertThrows(InsufficientFundsException.class,
            () -> l2.prepareUpdateWeight(301, RoutingAlgorithm.Collateralization.STRICT));


    // prepare another transaction in the opposite direction
    try {
      l2.prepareUpdateWeight(-500, RoutingAlgorithm.Collateralization.STRICT);
    } catch (InsufficientFundsException e) {
      fail("Prepare failed");
    }

    // prepare another transaction in the opposite direction
    try {
      l2.prepareUpdateWeight(-500, RoutingAlgorithm.Collateralization.STRICT);
    } catch (InsufficientFundsException e) {
      fail("Prepare failed");
    }

    assertTrue(l2.areFundsAvailable(-1000, RoutingAlgorithm.Collateralization.STRICT));
    assertFalse(l2.areFundsAvailable(-1001, RoutingAlgorithm.Collateralization.STRICT));
    assertTrue(l2.areFundsAvailable(300, RoutingAlgorithm.Collateralization.STRICT));
    assertFalse(l2.areFundsAvailable(301, RoutingAlgorithm.Collateralization.STRICT));

    // unlock funds and make sure they return to original state
    try {
      l2.finalizeUpdateWeight(500, RoutingAlgorithm.Collateralization.STRICT);
    } catch (TransactionFailedException e) {
      fail("Finalize update failed");
    }

    assertTrue(l2.areFundsAvailable(300, RoutingAlgorithm.Collateralization.STRICT));
    assertFalse(l2.areFundsAvailable(301, RoutingAlgorithm.Collateralization.STRICT));

    // try to prepare a transaction that doesn't have enough funds
    assertThrows(InsufficientFundsException.class,
            () -> l2.prepareUpdateWeight(301, RoutingAlgorithm.Collateralization.STRICT));

    assertTrue(l2.areFundsAvailable(-1500, RoutingAlgorithm.Collateralization.STRICT));
    assertFalse(l2.areFundsAvailable(-1501, RoutingAlgorithm.Collateralization.STRICT));

    try {
      l2.finalizeUpdateWeight(200, RoutingAlgorithm.Collateralization.STRICT);
    } catch (TransactionFailedException e) {
      fail("Finalize update failed");
    }

    assertTrue(l2.areFundsAvailable(300, RoutingAlgorithm.Collateralization.STRICT));
    assertFalse(l2.areFundsAvailable(301, RoutingAlgorithm.Collateralization.STRICT));

    // try to prepare a transaction that doesn't have enough funds
    assertThrows(InsufficientFundsException.class,
            () -> l2.prepareUpdateWeight(301, RoutingAlgorithm.Collateralization.STRICT));

    assertTrue(l2.areFundsAvailable(-1700, RoutingAlgorithm.Collateralization.STRICT));
    assertFalse(l2.areFundsAvailable(-1701, RoutingAlgorithm.Collateralization.STRICT));

    try {
      l2.finalizeUpdateWeight(-500, RoutingAlgorithm.Collateralization.STRICT);
    } catch (TransactionFailedException e) {
      fail("Finalize update failed");
    }

    assertTrue(l2.areFundsAvailable(800, RoutingAlgorithm.Collateralization.STRICT));
    assertFalse(l2.areFundsAvailable(801, RoutingAlgorithm.Collateralization.STRICT));
    assertTrue(l2.areFundsAvailable(-1700, RoutingAlgorithm.Collateralization.STRICT));
    assertFalse(l2.areFundsAvailable(-1701, RoutingAlgorithm.Collateralization.STRICT));

    // try to prepare a transaction that doesn't have enough funds
    assertThrows(InsufficientFundsException.class,
            () -> l2.prepareUpdateWeight(-1701, RoutingAlgorithm.Collateralization.STRICT));

    try {
      l2.finalizeUpdateWeight(-500, RoutingAlgorithm.Collateralization.STRICT);
    } catch (TransactionFailedException e) {
      fail("Finalize update failed");
    }

    assertTrue(l2.areFundsAvailable(1300, RoutingAlgorithm.Collateralization.STRICT));
    assertFalse(l2.areFundsAvailable(1301, RoutingAlgorithm.Collateralization.STRICT));
    // try to prepare a transaction that doesn't have enough funds
    assertThrows(InsufficientFundsException.class,
            () -> l2.prepareUpdateWeight(1301, RoutingAlgorithm.Collateralization.STRICT));

    assertTrue(l2.areFundsAvailable(-1700, RoutingAlgorithm.Collateralization.STRICT));
    assertFalse(l2.areFundsAvailable(-1701, RoutingAlgorithm.Collateralization.STRICT));
    // try to prepare a transaction that doesn't have enough funds
    assertThrows(InsufficientFundsException.class,
            () -> l2.prepareUpdateWeight(-1701, RoutingAlgorithm.Collateralization.STRICT));
  }

  @Test
  void testAreFundsAvailableTotalCollateral() {
    assertFalse(l1.areFundsAvailable(500, RoutingAlgorithm.Collateralization.TOTAL));
    assertFalse(l1.areFundsAvailable(-1001, RoutingAlgorithm.Collateralization.TOTAL));
    assertTrue(l1.areFundsAvailable(-500, RoutingAlgorithm.Collateralization.TOTAL));
    assertTrue(l1.areFundsAvailable(-1000, RoutingAlgorithm.Collateralization.TOTAL));

    // lock up some funds and make sure calculations are still correct
    try {
      l1.prepareUpdateWeight(-500, RoutingAlgorithm.Collateralization.TOTAL);
    } catch (InsufficientFundsException e) {
      fail("Prepare failed");
    }
    assertFalse(l1.areFundsAvailable(-500, RoutingAlgorithm.Collateralization.TOTAL));
    assertFalse(l1.areFundsAvailable(-1, RoutingAlgorithm.Collateralization.TOTAL));
    assertFalse(l1.areFundsAvailable(-501, RoutingAlgorithm.Collateralization.TOTAL));
    assertFalse(l1.areFundsAvailable(500, RoutingAlgorithm.Collateralization.TOTAL));

    // try to prepare a second valid transaction
    assertThrows(InsufficientFundsException.class,
            () -> l1.prepareUpdateWeight(-200, RoutingAlgorithm.Collateralization.TOTAL));

    assertFalse(l1.areFundsAvailable(-1, RoutingAlgorithm.Collateralization.TOTAL));

    // unlock funds and make sure they return to original state
    try {
      l1.finalizeUpdateWeight(-500, RoutingAlgorithm.Collateralization.TOTAL);
    } catch (TransactionFailedException e) {
      fail("Finalize update failed");
    }

    assertTrue(l1.areFundsAvailable(-500, RoutingAlgorithm.Collateralization.TOTAL));
    assertFalse(l1.areFundsAvailable(-501, RoutingAlgorithm.Collateralization.TOTAL));
    assertTrue(l1.areFundsAvailable(500, RoutingAlgorithm.Collateralization.TOTAL));
    assertFalse(l1.areFundsAvailable(501, RoutingAlgorithm.Collateralization.TOTAL));


    ////////////////////////// test mins
    assertTrue(l2.areFundsAvailable(1000, RoutingAlgorithm.Collateralization.TOTAL));
    assertTrue(l2.areFundsAvailable(-2000, RoutingAlgorithm.Collateralization.TOTAL));
    assertFalse(l2.areFundsAvailable(1001, RoutingAlgorithm.Collateralization.TOTAL));
    assertFalse(l2.areFundsAvailable(-2001, RoutingAlgorithm.Collateralization.TOTAL));

    // lock up some funds and make sure calculations are still correct
    try {
      l2.prepareUpdateWeight(500, RoutingAlgorithm.Collateralization.TOTAL);
    } catch (InsufficientFundsException e) {
      fail("Prepare failed");
    }
    assertFalse(l2.areFundsAvailable(-1, RoutingAlgorithm.Collateralization.TOTAL));
    assertFalse(l2.areFundsAvailable(1, RoutingAlgorithm.Collateralization.TOTAL));

    // try to prepare a second valid transaction
    assertThrows(InsufficientFundsException.class,
            () -> l2.prepareUpdateWeight(-200, RoutingAlgorithm.Collateralization.TOTAL));

    assertFalse(l2.areFundsAvailable(-1, RoutingAlgorithm.Collateralization.TOTAL));
    assertFalse(l2.areFundsAvailable(1, RoutingAlgorithm.Collateralization.TOTAL));

    // unlock funds and make sure they return to original state
    try {
      l2.finalizeUpdateWeight(500, RoutingAlgorithm.Collateralization.TOTAL);
    } catch (TransactionFailedException e) {
      fail("Finalize update failed");
    }

    assertTrue(l2.areFundsAvailable(500, RoutingAlgorithm.Collateralization.TOTAL));
    assertTrue(l2.areFundsAvailable(-2500, RoutingAlgorithm.Collateralization.TOTAL));
    assertFalse(l2.areFundsAvailable(501, RoutingAlgorithm.Collateralization.TOTAL));
    assertFalse(l2.areFundsAvailable(-2501, RoutingAlgorithm.Collateralization.TOTAL));

  }
  
  @Test
  void prepareUpdateWeight() {
  }

  @Test
  void finalizeUpdateWeight() {
  }
}