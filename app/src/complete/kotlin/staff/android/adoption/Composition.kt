package staff.android.adoption
import staff.android.adoption.storage.CartStorage
import staff.android.adoption.saved.SavedConsumer
import staff.android.adoption.cart.CartConsumer
import staff.android.adoption.legacy.LegacyCart
import staff.android.adoption.adapter.LegacyCartAdapter

internal fun createConsumers(storage: CartStorage): Consumers {
    val legacy = LegacyCart(storage)
    val port = LegacyCartAdapter(legacy)
    return Consumers(SavedConsumer(port), CartConsumer(port))
}
