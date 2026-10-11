package staff.android.adoption

import android.app.Application
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import staff.android.adoption.saved.SavedConsumer
import staff.android.adoption.cart.CartConsumer
import java.io.File

internal data class Consumers(val saved: SavedConsumer, val cart: CartConsumer)
data class ShopState(val loaded: Boolean = false, val quantity: Int = 0,
    val revision: Long = 0, val busy: Boolean = false, val message: String? = null, val error: Boolean = false)

/** Application ownership avoids cancelling a durable write when an Activity is replaced. */
class ShopGraph internal constructor(private val consumers: Consumers) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val state = MutableStateFlow(ShopState())
    init { refresh() }
    fun refresh() = action(recover = true) { null }
    fun addSaved() = action { consumers.saved.addTea() }
    fun addCart() = action { consumers.cart.addTea() }
    fun setQuantity(quantity: Int) = action { consumers.cart.setQuantity(quantity) }
    private fun action(recover: Boolean = false, mutate: () -> String?) {
        if (state.value.busy || (state.value.error && !recover)) return
        state.value = state.value.copy(busy = true, message = null, error = false)
        scope.launch {
            try {
                val next = withContext(Dispatchers.IO) {
                    val message = mutate()
                    val saved = consumers.saved.read()
                    val cart = consumers.cart.read()
                    check(saved.quantity == cart.quantity && saved.revision == cart.revision) { "Consumers disagree" }
                    ShopState(true, cart.quantity, cart.revision, message = message)
                }
                state.value = next
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                state.value = state.value.copy(busy = false, error = true,
                    message = "We couldn’t confirm your cart. Check device storage, then try again to read the saved state.")
            }
        }
    }
}
class ShopApplication : Application() {
    val graph by lazy { ShopGraph(createConsumers(CartFileStorage(File(filesDir, "cart-v1.txt")))) }
}
