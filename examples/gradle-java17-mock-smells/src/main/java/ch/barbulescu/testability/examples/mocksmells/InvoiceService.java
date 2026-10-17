package ch.barbulescu.testability.examples.mocksmells;

public class InvoiceService {

    public String header() {
        return "Invoice " + Clock.today().getYear();
    }
}
