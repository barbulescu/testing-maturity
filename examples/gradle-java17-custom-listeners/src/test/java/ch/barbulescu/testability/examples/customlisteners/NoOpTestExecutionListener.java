package ch.barbulescu.testability.examples.customlisteners;

import org.springframework.test.context.support.AbstractTestExecutionListener;

/**
 * Stands in for whatever a real project's own custom listener would be -
 * the point is simply that naming an explicit listener here replaces
 * Spring's default set (which is where the probe's spring.factories-registered
 * listener normally lives) rather than adding to it.
 */
class NoOpTestExecutionListener extends AbstractTestExecutionListener {
}
