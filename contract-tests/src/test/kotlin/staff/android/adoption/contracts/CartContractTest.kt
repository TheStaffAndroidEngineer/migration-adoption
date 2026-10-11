package staff.android.adoption.contracts

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import staff.android.adoption.api.CartSnapshot
import staff.android.adoption.storage.*
import java.io.IOException
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class CartContractTest {
    @Test fun seedSnapshotDoesNotMutateStorage() {
        val storage = TestStorage()
        assertEquals(CartSnapshot(3, 0), newPort(storage).snapshot())
        assertEquals(0, storage.callbacks)
    }
    @Test fun acceptedAdditionThenLimitRejectionPreservesRevisionAndBothValues() {
        val storage = TestStorage(); val port = newPort(storage)
        val accepted = port.addTea(1)
        assertTrue(accepted.accepted)
        assertEquals(CartSnapshot(4, 1), accepted.snapshot)
        assertEquals("Tea added to your cart.", accepted.message)
        val rejected = port.addTea(1)
        assertFalse(rejected.accepted)
        assertEquals(accepted.snapshot, rejected.snapshot)
        assertEquals("You can add up to 4 Tea. Your cart is unchanged.", rejected.message)
        assertEquals(CartRecord(4, 1), storage.read())
        assertEquals(2, storage.callbacks); assertEquals(1, storage.commits)
    }
    @Test fun nonpositiveAdditionsCannotBecomeRemoval() {
        val storage = TestStorage(); val port = newPort(storage)
        for (amount in listOf(0, -1, Int.MIN_VALUE)) {
            val edit = port.addTea(amount)
            assertFalse(edit.accepted); assertEquals(CartSnapshot(3, 0), edit.snapshot)
            assertEquals("Choose a valid quantity.", edit.message)
        }
        assertEquals(0, storage.commits)
    }
    @Test fun largePositiveAdditionDoesNotWrapIntoAcceptedQuantity() {
        val storage = TestStorage()
        assertFalse(newPort(storage).addTea(Int.MAX_VALUE).accepted)
        assertEquals(CartRecord(3, 0), storage.read())
    }
    @Test fun settingZeroRemovesAllTeaAndAdvancesExactlyOneRevision() {
        val port = newPort(TestStorage(CartRecord(4, 19)))
        val edit = port.setTea(0)
        assertTrue(edit.accepted); assertEquals(CartSnapshot(0, 20), edit.snapshot)
        assertEquals("Cart updated.", edit.message)
    }
    @Test fun settingCurrentQuantityIsAcceptedWithoutInventingARevision() {
        val storage = TestStorage(CartRecord(2, 7))
        val edit = newPort(storage).setTea(2)
        assertTrue(edit.accepted); assertEquals(CartSnapshot(2, 7), edit.snapshot)
        assertEquals(1, storage.callbacks); assertEquals(0, storage.commits)
    }
    @Test fun replacementCannotBypassTheLimit() {
        val storage = TestStorage(); val port = newPort(storage)
        for (quantity in listOf(5, Int.MAX_VALUE, -1, Int.MIN_VALUE)) {
            assertFalse(port.setTea(quantity).accepted)
            assertEquals(CartRecord(3, 0), storage.read())
        }
    }
    @Test fun rejectedRequestDoesNotPreventLaterValidRequest() {
        val port = newPort(TestStorage())
        assertFalse(port.addTea(2).accepted)
        assertEquals(CartSnapshot(4, 1), port.addTea(1).snapshot)
    }
    @Test fun replacementAndAdditionShareOneRevisionSequence() {
        val port = newPort(TestStorage())
        assertEquals(CartSnapshot(1, 1), port.setTea(1).snapshot)
        assertEquals(CartSnapshot(3, 2), port.addTea(2).snapshot)
        assertEquals(CartSnapshot(4, 3), port.setTea(4).snapshot)
    }
    @Test fun aNewProviderReadsThePreviouslyAcceptedRecord() {
        val storage = TestStorage()
        newPort(storage).addTea(1)
        val restartedProvider = newPort(storage)
        assertEquals(CartSnapshot(4, 1), restartedProvider.snapshot())
        assertFalse(restartedProvider.addTea(1).accepted)
    }
    @Test fun returnedSnapshotIsNotAnAliasForLaterState() {
        val port = newPort(TestStorage())
        val old = port.snapshot()
        port.addTea(1)
        assertEquals(CartSnapshot(3, 0), old)
        assertEquals(CartSnapshot(4, 1), port.snapshot())
    }
    @Test fun revisionOverflowCannotCommitAnUnversionedMutation() {
        val storage = TestStorage(CartRecord(3, Long.MAX_VALUE))
        assertThrows(ArithmeticException::class.java) { newPort(storage).addTea(1) }
        assertEquals(CartRecord(3, Long.MAX_VALUE), storage.read())
        assertEquals(0, storage.commits)
    }
    @Test fun revisionExhaustionStillAllowsRejectionAndSameValueAcknowledgement() {
        val port = newPort(TestStorage(CartRecord(4, Long.MAX_VALUE)))
        assertFalse(port.addTea(1).accepted)
        assertTrue(port.setTea(4).accepted)
        assertEquals(CartSnapshot(4, Long.MAX_VALUE), port.snapshot())
    }
    @Test fun precommitFailureReturnsNoSuccessAndLeavesTheKnownOldRecord() {
        val storage = TestStorage(); storage.failBeforeCommit = true
        val port = newPort(storage)
        assertEquals("before commit", assertThrows(IOException::class.java) { port.addTea(1) }.message)
        assertEquals(CartRecord(3, 0), storage.read())
        assertEquals(1, storage.callbacks); assertEquals(0, storage.commits)
        storage.failBeforeCommit = false
        assertEquals(CartSnapshot(4, 1), port.addTea(1).snapshot)
    }
    @Test fun readFailureCannotBePresentedAsAnEmptyOrSeededCart() {
        val storage = TestStorage(); storage.failRead = true
        assertThrows(IOException::class.java) { newPort(storage).snapshot() }
    }
    @Test fun concurrentCommandsAcrossProvidersHaveOnlyFourAcceptedUnits() {
        val storage = TestStorage(CartRecord(0, 0))
        val pool = Executors.newFixedThreadPool(8); val start = CountDownLatch(1)
        try {
            val results = (1..32).map {
                pool.submit(Callable { start.await(); newPort(storage).addTea(1) })
            }
            start.countDown()
            val edits = results.map { it.get(5, TimeUnit.SECONDS) }
            assertEquals(4, edits.count { it.accepted })
            assertEquals(setOf(1L, 2L, 3L, 4L), edits.filter { it.accepted }.map { it.snapshot.revision }.toSet())
            assertEquals(CartRecord(4, 4), storage.read())
            assertEquals(32, storage.callbacks); assertEquals(4, storage.commits)
        } finally { start.countDown(); pool.shutdownNow() }
    }
}
