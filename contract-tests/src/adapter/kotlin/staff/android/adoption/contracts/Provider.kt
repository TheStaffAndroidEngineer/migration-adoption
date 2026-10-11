package staff.android.adoption.contracts
import staff.android.adoption.api.CartPort
import staff.android.adoption.adapter.LegacyCartAdapter
import staff.android.adoption.legacy.LegacyCart
import staff.android.adoption.storage.CartStorage
internal fun newPort(storage: CartStorage): CartPort = LegacyCartAdapter(LegacyCart(storage))
