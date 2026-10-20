package ch.barbulescu.testability.examples.mockk

import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/** Two @MockK fields, created by the extension for the test's instance. */
@ExtendWith(MockKExtension::class)
class AnnotatedMocksTest {

    @MockK
    lateinit var taxRates: TaxRates

    @RelaxedMockK
    lateinit var unusedRates: TaxRates

    @Test
    fun usesStubbedRate() {
        every { taxRates.percentFor("CH") } returns 50

        assertEquals(150, PriceCalculator(taxRates).grossCents(100, "CH"))
    }
}
