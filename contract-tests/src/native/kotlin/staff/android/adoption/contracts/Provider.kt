package staff.android.adoption.contracts
import staff.android.adoption.api.CartPort
import staff.android.adoption.engine.NativeCart
import staff.android.adoption.storage.CartStorage
internal fun newPort(storage: CartStorage): CartPort = NativeCart(storage)
