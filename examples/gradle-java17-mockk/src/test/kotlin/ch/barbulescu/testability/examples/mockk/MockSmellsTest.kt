package ch.barbulescu.testability.examples.mockk

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.spyk
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** One smell per test: an object mock (like a static mock), a spy, and a chain stubbed in one go. */
class MockSmellsTest {

    @AfterEach
    fun unmock() {
        unmockkAll()
    }

    @Test
    fun mocksASingletonObject() {
        mockkObject(RoundingRules)
        every { RoundingRules.round(any()) } returns 100

        assertEquals(100, PriceCalculator(FixedTaxRates()).grossCents(101, "CH"))
    }

    @Test
    fun spiesOnARealObject() {
        val calculator = spyk(PriceCalculator(FixedTaxRates()))
        every { calculator.grossCents(1, "CH") } returns 2

        assertEquals(2, calculator.grossCents(1, "CH"))
        assertEquals(108, calculator.grossCents(100, "CH"))
    }

    @Test
    fun stubsAChainInOneGo() {
        val catalog = mockk<Catalog>()
        // MockK creates a child mock for catalog.rates, which isn't counted.
        every { catalog.rates.percentFor("CH") } returns 10

        assertEquals(110, PriceCalculator(catalog.rates).grossCents(100, "CH"))
    }
}
