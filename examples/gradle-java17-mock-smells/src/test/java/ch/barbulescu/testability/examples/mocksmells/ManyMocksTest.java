package ch.barbulescu.testability.examples.mocksmells;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Six mocks for one test: the test knows too much about how the code is wired together. */
@ExtendWith(MockitoExtension.class)
class ManyMocksTest {

    @Mock
    Order order;
    @Mock
    Customer customer;
    @Mock
    Address address;
    @Mock
    Order otherOrder;
    @Mock
    Customer otherCustomer;
    @Mock
    Address otherAddress;

    @Test
    void wiresSixMocks() {
        assertNotNull(order);
        assertNotNull(otherAddress);
    }
}
