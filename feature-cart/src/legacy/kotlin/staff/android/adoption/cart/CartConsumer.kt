package staff.android.adoption.cart

import staff.android.adoption.legacy.LegacyCart

class CartConsumer(private val cart: LegacyCart) {
    fun read(): CartState = cart.read().let { CartState(it.quantity, it.revision) }
    fun addTea(): String = cart.increase(1).notice
    fun setQuantity(quantity: Int): String = cart.replace(quantity).notice
}
