package staff.android.adoption

import android.util.AtomicFile
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import staff.android.adoption.storage.*
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class StorageCompositionTest {
    private lateinit var directory: File
    private lateinit var file: File
    @Before fun prepare() {
        directory = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "cart-contract-${UUID.randomUUID()}").apply { check(mkdirs()) }
        file = File(directory, "cart-v1.txt")
    }
    @After fun cleanup() { directory.deleteRecursively() }

    @Test fun missingRecordIsSeededButNotFalselyReportedAsPersisted() {
        assertEquals(CartRecord(3, 0), CartFileStorage(file).read())
        assertFalse(file.exists())
    }
    @Test fun acceptedRecordSurvivesNewStoreInstanceWithExactFormat() {
        val storage = CartFileStorage(file)
        assertEquals(CartRecord(4, 1), storage.update { CartRecord(it.quantity + 1, it.revision + 1) })
        assertEquals("1|4|1", file.readText())
        assertEquals(CartRecord(4, 1), CartFileStorage(file).read())
    }
    @Test fun corruptExistingRecordIsNotSilentlySeededOrOverwritten() {
        file.writeText("2|4|91")
        val storage = CartFileStorage(file)
        try { storage.read(); fail("Unsupported version should fail") } catch (_: IllegalArgumentException) { }
        var called = false
        try { storage.update { called = true; CartRecord(3, 0) }; fail("Must not overwrite corruption") }
        catch (_: IllegalArgumentException) { }
        assertFalse(called); assertEquals("2|4|91", file.readText())
    }
    @Test fun legacyAtomicBackupIsRecoveredBeforeReading() {
        file.writeText("broken")
        File(file.path + ".bak").writeText("1|4|12")
        assertEquals(CartRecord(4, 12), CartFileStorage(file).read())
        assertEquals("1|4|12", file.readText())
    }
    @Test fun unfinishedReplacementDoesNotDisplaceAnExistingCommittedRecord() {
        file.writeText("1|3|7")
        File(file.path + ".new").writeText("1|4|")
        assertEquals(CartRecord(3, 7), CartFileStorage(file).read())
        assertEquals("1|3|7", file.readText())
    }
    @Test fun transformFailureOccursBeforeWritingAndPreservesOldBytes() {
        file.writeText("1|3|7")
        val storage = CartFileStorage(file)
        try { storage.update { throw IOException("known before commit") }; fail("Expected failure") }
        catch (error: IOException) { assertEquals("known before commit", error.message) }
        assertEquals("1|3|7", file.readText())
        assertEquals(CartRecord(3, 7), storage.read())
    }
    @Test fun realStoreSerializesConcurrentReadTransformPersist() {
        file.writeText("1|0|0")
        val storage = CartFileStorage(file)
        val pool = Executors.newFixedThreadPool(6); val start = CountDownLatch(1)
        try {
            val calls = (1..24).map { pool.submit(Callable {
                start.await()
                storage.update { if (it.quantity < 4) CartRecord(it.quantity + 1, it.revision + 1) else it }
            }) }
            start.countDown(); calls.forEach { it.get(10, TimeUnit.SECONDS) }
            assertEquals(CartRecord(4, 4), CartFileStorage(file).read())
        } finally { start.countDown(); pool.shutdownNow() }
    }
    @Test fun selectedStageCompositionUsesOnePersistentAcceptedCart() {
        val consumers = createConsumers(CartFileStorage(file))
        assertEquals(3, consumers.saved.read().quantity)
        assertEquals("Tea added to your cart.", consumers.saved.addTea())
        assertEquals(4, consumers.cart.read().quantity); assertEquals(1L, consumers.cart.read().revision)
        assertEquals("You can add up to 4 Tea. Your cart is unchanged.", consumers.cart.addTea())
        assertEquals(4, consumers.saved.read().quantity); assertEquals(1L, consumers.saved.read().revision)
        val recreated = createConsumers(CartFileStorage(file))
        assertEquals(4, recreated.cart.read().quantity)
        assertEquals("Cart updated.", recreated.cart.setQuantity(0))
        assertEquals(0, recreated.saved.read().quantity); assertEquals(2L, recreated.saved.read().revision)
    }
    @Test fun uncertainMutationBlocksFurtherCommandsUntilAuthoritativeRefresh() = runBlocking<Unit> {
        // A post-commit exception may leave the new record. This models uncertainty,
        // not a promise to make real filesystem failure happen at this exact point.
        val storage = object : CartStorage {
            var current = CartRecord(3, 0)
            var updates = 0
            @Synchronized override fun read() = current
            @Synchronized override fun update(transform: (CartRecord) -> CartRecord): CartRecord {
                updates++; current = transform(current)
                throw IOException("commit acknowledgement unavailable")
            }
        }
        val graph = withContext(Dispatchers.Main) { ShopGraph(createConsumers(storage)) }
        withTimeout(5_000) { graph.state.first { it.loaded && !it.busy } }
        withContext(Dispatchers.Main) { graph.addSaved() }
        val failed = withTimeout(5_000) { graph.state.first { it.error && !it.busy } }
        assertEquals(3, failed.quantity); assertEquals(0L, failed.revision)
        assertEquals(CartRecord(4, 1), storage.read())
        withContext(Dispatchers.Main) { graph.addSaved(); graph.addCart(); graph.setQuantity(2) }
        assertEquals(1, storage.updates)
        withContext(Dispatchers.Main) { graph.refresh() }
        val recovered = withTimeout(5_000) { graph.state.first { it.loaded && !it.busy && !it.error } }
        assertEquals(4, recovered.quantity); assertEquals(1L, recovered.revision)
        assertEquals(1, storage.updates)
    }
}
