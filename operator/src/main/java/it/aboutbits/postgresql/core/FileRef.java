package it.aboutbits.postgresql.core;

import io.fabric8.generator.annotation.Required;
import io.fabric8.generator.annotation.ValidationRule;
import lombok.Getter;
import lombok.Setter;
import org.jspecify.annotations.NullMarked;

/// A reference to a file inside the operator container.
///
/// This class is used wherever a CRD spec needs to point to a specific file
/// The [#path] field identifies the file location within the container.
///
/// ### Example usage in a CR manifest
///
/// ```yaml
///  spec:
///     adminSecretFileRef:
///         path: "/mnt/secrets/db-credentials.json"
/// ```
@Getter
@Setter
@NullMarked
public class FileRef {
    /// The path to the file.
    /// Must not be blank.
    @Required
    @ValidationRule(
            value = "self.trim().size() > 0",
            message = "The path must not be empty."
    )
    private String path = "";
}
