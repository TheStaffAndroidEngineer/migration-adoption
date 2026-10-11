package staff.android.adoption.adapter

import staff.android.adoption.api.*
import staff.android.adoption.legacy.*
import staff.android.adoption.storage.CartRecord

class LegacyCartAdapter(private val legacy: LegacyCart) : CartPort {
    override fun snapshot() = legacy.read().asSnapshot()
    override fun addTea(amount: Int) = legacy.increase(amount).asEdit()
    override fun setTea(quantity: Int) = legacy.replace(quantity).asEdit()
    private fun CartRecord.asSnapshot() = CartSnapshot(quantity, revision)
    private fun LegacyDecision.asEdit() = CartEdit(allowed, record.asSnapshot(), notice)
}
