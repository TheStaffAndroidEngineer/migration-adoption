package staff.android.adoption

import android.util.AtomicFile
import staff.android.adoption.storage.*
import java.io.File
import java.io.FileNotFoundException

/** This instance is the serialization boundary; never create a store per screen.
 * AtomicFile supplies replacement/recovery, not locking or multi-process coordination. */
class CartFileStorage(file: File) : CartStorage {
    private val file = AtomicFile(file)
    @Synchronized override fun read(): CartRecord = try {
        file.openRead().bufferedReader(Charsets.UTF_8).use { CartFormat.decode(it.readText()) }
    } catch (missing: FileNotFoundException) {
        // A genuinely absent record is the seeded checkpoint. An unreadable existing
        // record is an error; silently replacing it would erase a shopper's cart.
        if (file.baseFile.exists()) throw missing
        CartRecord(3, 0)
    }
    @Synchronized override fun update(transform: (CartRecord) -> CartRecord): CartRecord {
        val before = read()
        val after = transform(before)
        if (after == before) return before
        val stream = file.startWrite()
        try {
            stream.write(CartFormat.encode(after).toByteArray(Charsets.UTF_8))
            stream.fd.sync()
            file.finishWrite(stream)
        } catch (failure: Throwable) { file.failWrite(stream); throw failure }
        // AtomicFile can log a rename failure; verify the authoritative record.
        // A readback failure after commit is uncertain and must trigger a reread.
        check(read() == after) { "Cart replacement could not be confirmed" }
        return after
    }
}
