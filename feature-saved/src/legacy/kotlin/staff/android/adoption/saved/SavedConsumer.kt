package staff.android.adoption.saved

import staff.android.adoption.legacy.LegacyCart

class SavedConsumer(private val cart: LegacyCart) {
    fun read(): SavedState = cart.read().let { SavedState(it.quantity, it.revision) }
    fun addTea(): String = cart.increase(1).notice
}
