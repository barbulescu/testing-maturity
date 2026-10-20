package ch.barbulescu.testability.examples.mockk

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Only one of the two tests creates a mock. */
class InlineMockTest {

    @Test
    fun withInlineMock() {
        val taxRates = mockk<TaxRates>()
        every { taxRates.percentFor("CH") } returns 0

        assertEquals(100, PriceCalculator(taxRates).grossCents(100, "CH"))
    }

    @Test
    fun withoutMock() {
        assertEquals(108, PriceCalculator(FixedTaxRates()).grossCents(100, "CH"))
    }
}
