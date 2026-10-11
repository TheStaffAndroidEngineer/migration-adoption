package staff.android.adoption.legacy

import staff.android.adoption.storage.CartRecord
import staff.android.adoption.storage.CartStorage

data class LegacyDecision(val allowed: Boolean, val record: CartRecord, val notice: String)
class LegacyCart(private val storage: CartStorage) {
    fun read(): CartRecord = storage.read()
    fun increase(amount: Int): LegacyDecision = change(amount, true)
    fun replace(quantity: Int): LegacyDecision = change(quantity, false)
    private fun change(value: Int, adding: Boolean): LegacyDecision {
        var allowed = false
        var notice = ""
        val record = storage.update { current ->
            val proposed = if (adding) current.quantity.toLong() + value else value.toLong()
            when {
                (adding && value <= 0) || proposed < 0 -> {
                    notice = "Choose a valid quantity."; current
                }
                proposed > 4 -> {
                    notice = "You can add up to 4 Tea. Your cart is unchanged."; current
                }
                else -> {
                    allowed = true
                    notice = if (adding) "Tea added to your cart." else "Cart updated."
                    if (proposed.toInt() == current.quantity) current
                    else CartRecord(proposed.toInt(), Math.addExact(current.revision, 1))
                }
            }
        }
        return LegacyDecision(allowed, record, notice)
    }
}
