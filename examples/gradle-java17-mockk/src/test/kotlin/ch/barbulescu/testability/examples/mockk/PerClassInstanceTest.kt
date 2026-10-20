package ch.barbulescu.testability.examples.mockk

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/** One instance for the whole class, so its mock is shared by both tests (class level). */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PerClassInstanceTest {

    private val taxRates = mockk<TaxRates>().also { every { it.percentFor(any()) } returns 10 }

    @Test
    fun swiss() {
        assertEquals(110, PriceCalculator(taxRates).grossCents(100, "CH"))
    }

    @Test
    fun german() {
        assertEquals(110, PriceCalculator(taxRates).grossCents(100, "DE"))
    }
}
