package ch.barbulescu.testability.examples.contextfails;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Deliberately fails context startup: {@code .invalid} is reserved by
 * RFC 2606 to never resolve, so this reliably throws
 * {@link UnknownHostException} - the exact scenario this example proves
 * the probe captures (root cause type only, never the message).
 */
@Component
public class UnreachableHostChecker {

    @PostConstruct
    void checkHostIsReachable() throws UnknownHostException {
        InetAddress.getByName("nonexistent-host-xyz.invalid");
    }
}
