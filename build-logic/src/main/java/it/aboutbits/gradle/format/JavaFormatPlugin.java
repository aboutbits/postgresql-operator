package it.aboutbits.gradle.format;

import com.diffplug.gradle.spotless.SpotlessExtension;
import com.diffplug.gradle.spotless.SpotlessPlugin;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.VersionCatalogsExtension;
import org.gradle.api.artifacts.VersionConstraint;

/// Applies Spotless with Google Java Format in AOSP style, followed by [JavaLayoutStep].
///
/// The settings are the ones of the RFC proposal: AOSP import order and the line length of
/// [JavaLayout#LINE_LENGTH]. The project that applies this plugin sets the `target` of the `java` format itself.
public class JavaFormatPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(SpotlessPlugin.class);

        var googleJavaFormatVersion = project.getExtensions()
                .getByType(VersionCatalogsExtension.class)
                .named("libs")
                .findVersion("googleJavaFormat")
                .map(VersionConstraint::getRequiredVersion)
                .orElseThrow(() -> new GradleException("The version catalog has no version 'googleJavaFormat'"));

        project.getExtensions().configure(SpotlessExtension.class, spotless -> spotless.java(java -> {
            java.googleJavaFormat(googleJavaFormatVersion)
                    .aosp()
                    .reorderImports(true)
                    // The layout step uses the same value, so it never makes a line longer than the formatter allows
                    .maxLineLength(JavaLayout.LINE_LENGTH);
            java.addStep(JavaLayoutStep.create());
        }));
    }
}
