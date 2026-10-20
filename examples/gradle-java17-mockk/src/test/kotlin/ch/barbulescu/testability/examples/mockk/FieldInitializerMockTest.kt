package ch.barbulescu.testability.examples.mockk

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** A new instance per test, so each test has one mock of its own. */
class FieldInitializerMockTest {

    private val taxRates = mockk<TaxRates>()

    @Test
    fun swissRate() {
        every { taxRates.percentFor("CH") } returns 8

        assertEquals(108, PriceCalculator(taxRates).grossCents(100, "CH"))
    }

    @Test
    fun germanRate() {
        every { taxRates.percentFor("DE") } returns 19

        assertEquals(119, PriceCalculator(taxRates).grossCents(100, "DE"))
    }
}
