package staff.android.adoption
import staff.android.adoption.storage.CartStorage
import staff.android.adoption.saved.SavedConsumer
import staff.android.adoption.cart.CartConsumer
import staff.android.adoption.engine.NativeCart

internal fun createConsumers(storage: CartStorage): Consumers {
    val port = NativeCart(storage)
    return Consumers(SavedConsumer(port), CartConsumer(port))
}
