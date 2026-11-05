package it.aboutbits.gradle.format;

import com.diffplug.spotless.FormatterFunc;
import com.diffplug.spotless.FormatterStep;

/// The Spotless step that runs [JavaLayout] after Google Java Format.
public final class JavaLayoutStep {
    /// Spotless decides from this value if the step changed, because the rules live in code.
    /// Increase it with every change of a rule, otherwise up-to-date checks can keep stale results.
    private static final int RULES_VERSION = 1;

    private JavaLayoutStep() {
    }

    public static FormatterStep create() {
        return FormatterStep.create("aboutbits-java-layout", RULES_VERSION, JavaLayoutStep::formatter);
    }

    private static FormatterFunc formatter(Integer rulesVersion) {
        return JavaLayout::format;
    }
}
