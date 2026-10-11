package staff.android.adoption.engine

import staff.android.adoption.api.*
import staff.android.adoption.storage.*

class NativeCart(private val storage: CartStorage) : CartPort {
    override fun snapshot() = storage.read().let { CartSnapshot(it.quantity, it.revision) }
    override fun addTea(amount: Int) = mutate(amount, true)
    override fun setTea(quantity: Int) = mutate(quantity, false)
    private fun mutate(value: Int, adding: Boolean): CartEdit {
        var accepted = false
        var message = ""
        val record = storage.update { current ->
            val proposed = if (adding) current.quantity.toLong() + value else value.toLong()
            when {
                (adding && value <= 0) || proposed < 0 -> {
                    message = "Choose a valid quantity."; current
                }
                proposed > 4 -> {
                    message = "You can add up to 4 Tea. Your cart is unchanged."; current
                }
                else -> {
                    accepted = true
                    message = if (adding) "Tea added to your cart." else "Cart updated."
                    if (proposed.toInt() == current.quantity) current
                    else CartRecord(proposed.toInt(), Math.addExact(current.revision, 1))
                }
            }
        }
        return CartEdit(accepted, CartSnapshot(record.quantity, record.revision), message)
    }
}
