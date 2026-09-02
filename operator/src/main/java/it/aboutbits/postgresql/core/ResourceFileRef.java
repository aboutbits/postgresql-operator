package it.aboutbits.postgresql.core;

import io.fabric8.generator.annotation.Required;
import io.fabric8.generator.annotation.ValidationRule;
import lombok.Getter;
import lombok.Setter;
import org.jspecify.annotations.NullMarked;

/// A reference to a file inside an AWS Secrets Manager secret.
///
/// This class is used wherever a CRD spec needs to point to a specific file
/// within an AWS secret. The [#path] field identifies the file location
/// inside the secret.
///
/// ### Example usage in a CR manifest
///
/// ```yaml
///  spec:
///     adminSecretFileRef:
///         path: "/mnt/db-password"
/// ```
@Getter
@Setter
@NullMarked
public class ResourceFileRef {
    /// The path to the file inside the AWS Secrets Manager secret.
    /// Must not be blank.
    @Required
    @ValidationRule(
            value = "self.trim().size() > 0",
            message = "The path must not be empty."
    )
    private String path = "";
}
