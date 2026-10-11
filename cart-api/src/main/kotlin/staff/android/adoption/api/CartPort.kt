package staff.android.adoption.api

data class CartSnapshot(val quantity: Int, val revision: Long)
data class CartEdit(val accepted: Boolean, val snapshot: CartSnapshot, val message: String)
/** Local single-product teaching port. Calls may do I/O; callers use a worker dispatcher. */
interface CartPort {
    fun snapshot(): CartSnapshot
    fun addTea(amount: Int): CartEdit
    fun setTea(quantity: Int): CartEdit
}
