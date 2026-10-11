package staff.android.adoption.contracts

import staff.android.adoption.storage.*
import java.io.IOException

/** Models only a known failure before commit, not an uncertain filesystem outcome. */
internal class TestStorage(initial: CartRecord = CartRecord(3, 0)) : CartStorage {
    private var current = initial
    var callbacks = 0
        private set
    var commits = 0
        private set
    var failBeforeCommit = false
    var failRead = false
    @Synchronized override fun read(): CartRecord {
        if (failRead) throw IOException("read unavailable")
        return current
    }
    @Synchronized override fun update(transform: (CartRecord) -> CartRecord): CartRecord {
        callbacks++
        val next = transform(current)
        if (failBeforeCommit) throw IOException("before commit")
        if (next != current) commits++
        current = next
        return current
    }
}
