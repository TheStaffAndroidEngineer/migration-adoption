package staff.android.adoption.contracts

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import staff.android.adoption.adapter.LegacyCartAdapter
import staff.android.adoption.engine.NativeCart
import staff.android.adoption.legacy.LegacyCart
import staff.android.adoption.storage.*
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** This source set belongs only to the explicit comparison graph, never retirement proof. */
class CoexistenceTest {
    @Test fun directLegacyAndPortObserveOneAcceptedAuthority() {
        val storage = TestStorage(); val legacy = LegacyCart(storage); val adapter = LegacyCartAdapter(legacy)
        assertTrue(adapter.addTea(1).accepted)
        assertEquals(CartRecord(4, 1), legacy.read())
        assertFalse(legacy.increase(1).allowed)
        assertEquals(4, adapter.snapshot().quantity); assertEquals(1L, adapter.snapshot().revision)
    }
    @Test fun adapterNativeAndLegacyCanReadAndWriteTheUnchangedRepresentation() {
        val storage = TestStorage(); val adapter = LegacyCartAdapter(LegacyCart(storage))
        adapter.addTea(1)
        val encoded = CartFormat.encode(storage.read())
        val reopened = TestStorage(CartFormat.decode(encoded)); val native = NativeCart(reopened)
        assertEquals(4, native.snapshot().quantity); assertFalse(native.addTea(1).accepted)
        assertTrue(native.setTea(2).accepted)
        val olderReader = LegacyCart(TestStorage(CartFormat.decode(CartFormat.encode(reopened.read()))))
        assertEquals(CartRecord(2, 2), olderReader.read())
        assertEquals(CartRecord(3, 3), olderReader.increase(1).record)
    }
    @Test fun bothImplementationsHaveEquivalentObservableResultsAcrossMixedCommands() {
        val adapter = LegacyCartAdapter(LegacyCart(TestStorage()))
        val native = NativeCart(TestStorage())
        for ((add, value) in listOf(true to 2, false to 0, true to 4, true to 1, false to 4,
            false to -1, false to 2, true to Int.MAX_VALUE, true to 1)) {
            assertEquals(if (add) adapter.addTea(value) else adapter.setTea(value),
                if (add) native.addTea(value) else native.setTea(value))
        }
    }
    @Test fun concurrentOldAndNewEntrypointsCannotBypassTheSharedLimit() {
        val storage = TestStorage(CartRecord(0, 0)); val legacy = LegacyCart(storage)
        val adapter = LegacyCartAdapter(legacy); val native = NativeCart(storage)
        val pool = Executors.newFixedThreadPool(6); val start = CountDownLatch(1)
        try {
            val pending = (0 until 24).map { i -> pool.submit(Callable {
                start.await()
                when (i % 3) { 0 -> legacy.increase(1).allowed; 1 -> adapter.addTea(1).accepted; else -> native.addTea(1).accepted }
            }) }
            start.countDown()
            assertEquals(4, pending.count { it.get(5, TimeUnit.SECONDS) })
            assertEquals(CartRecord(4, 4), storage.read())
        } finally { start.countDown(); pool.shutdownNow() }
    }
}
