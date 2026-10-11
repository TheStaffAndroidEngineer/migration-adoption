package staff.android.adoption.contracts

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import staff.android.adoption.storage.*

class CartFormatTest {
    @Test fun canonicalRecordsRoundTripIncludingRevisionBoundary() {
        for (quantity in 0..4) for (revision in listOf(0L, 1L, Long.MAX_VALUE)) {
            val record = CartRecord(quantity, revision)
            assertEquals("1|$quantity|$revision", CartFormat.encode(record))
            assertEquals(record, CartFormat.decode(CartFormat.encode(record)))
        }
    }
    @Test fun invalidStateCannotBeConstructed() {
        for (quantity in listOf(-1, 5, Int.MAX_VALUE))
            assertThrows(IllegalArgumentException::class.java) { CartRecord(quantity, 0) }
        assertThrows(IllegalArgumentException::class.java) { CartRecord(1, -1) }
    }
    @Test fun unsupportedTruncatedAndExtraFieldsAreRejected() {
        for (value in listOf("", "1", "1|3", "2|3|0", "1|3|0|", "1|3|0|ignored"))
            assertThrows(IllegalArgumentException::class.java, { CartFormat.decode(value) }, value)
    }
    @Test fun numericalAliasesWhitespaceAndOutOfRangeValuesAreRejected() {
        for (value in listOf("1|03|0", "1|+3|0", "1|3|00", "1|3|+1", "1|3|-1",
            "1|5|0", "1|3|9223372036854775808", "1|3|0\n", " 1|3|0", "1|３|0"))
            assertThrows(IllegalArgumentException::class.java, { CartFormat.decode(value) }, value)
    }
}
