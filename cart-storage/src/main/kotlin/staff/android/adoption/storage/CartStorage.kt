package staff.android.adoption.storage

data class CartRecord(val quantity: Int, val revision: Long) {
    init { require(quantity in 0..4); require(revision >= 0) }
}
/** One shared instance serializes read-transform-persist in this single-process fixture.
 * update returns only after its persistence boundary returns. Precommit failure leaves
 * the old record; interruption at commit can recover the old or new valid record.
 * Reread before retrying an uncertain mutation. The callback executes once under
 * the same lock as read and must not re-enter storage. */
interface CartStorage {
    fun read(): CartRecord
    fun update(transform: (CartRecord) -> CartRecord): CartRecord
}
object CartFormat {
    fun encode(record: CartRecord) = "1|${record.quantity}|${record.revision}"
    fun decode(value: String): CartRecord {
        val parts = value.split('|')
        require(parts.size == 3 && parts[0] == "1") { "Unsupported cart record" }
        require(parts[1].matches(Regex("[0-4]")) && parts[2].matches(Regex("0|[1-9][0-9]*"))) { "Noncanonical cart record" }
        return CartRecord(parts[1].toInt(), parts[2].toLong())
    }
}
