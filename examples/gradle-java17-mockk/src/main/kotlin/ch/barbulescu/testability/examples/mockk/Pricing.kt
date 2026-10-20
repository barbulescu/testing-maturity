package ch.barbulescu.testability.examples.mockk

interface TaxRates {
    fun percentFor(country: String): Int
}

interface Catalog {
    val rates: TaxRates
}

object RoundingRules {
    fun round(cents: Int): Int = cents
}

open class PriceCalculator(private val taxRates: TaxRates) {
    open fun grossCents(netCents: Int, country: String): Int =
        RoundingRules.round(netCents + netCents * taxRates.percentFor(country) / 100)
}

class FixedTaxRates : TaxRates {
    override fun percentFor(country: String): Int = if (country == "CH") 8 else 20
}
