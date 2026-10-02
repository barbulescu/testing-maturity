package ch.barbulescu.testability.probe.mockito;

import ch.barbulescu.testability.probe.core.Safe;
import ch.barbulescu.testability.probe.core.TestNodeTracker;
import org.mockito.listeners.MockCreationListener;
import org.mockito.mock.MockCreationSettings;

/** Spies go through mock creation too, so they are counted as well. */
final class ProbeMockCreationListener implements MockCreationListener {

    @Override
    public void onMockCreated(Object mock, MockCreationSettings settings) {
        Safe.run(() -> TestNodeTracker.getInstance().mockCreatedOnCurrentThread());
    }

    // Deliberately no @Override: Mockito only declares this (as a default method) since 3.5,
    // and the probe compiles against 2.1. On newer versions it overrides and counts mockStatic().
    public void onStaticMockCreated(Class<?> mock, MockCreationSettings settings) {
        Safe.run(() -> TestNodeTracker.getInstance().mockCreatedOnCurrentThread());
    }
}
