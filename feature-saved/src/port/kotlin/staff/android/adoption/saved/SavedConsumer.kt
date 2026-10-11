package staff.android.adoption.saved

import staff.android.adoption.api.CartPort

class SavedConsumer(private val cart: CartPort) {
    fun read(): SavedState = cart.snapshot().let { SavedState(it.quantity, it.revision) }
    fun addTea(): String = cart.addTea(1).message
}
