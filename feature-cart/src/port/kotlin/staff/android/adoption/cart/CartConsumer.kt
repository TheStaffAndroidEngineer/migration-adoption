package staff.android.adoption.cart

import staff.android.adoption.api.CartPort

class CartConsumer(private val cart: CartPort) {
    fun read(): CartState = cart.snapshot().let { CartState(it.quantity, it.revision) }
    fun addTea(): String = cart.addTea(1).message
    fun setQuantity(quantity: Int): String = cart.setTea(quantity).message
}
