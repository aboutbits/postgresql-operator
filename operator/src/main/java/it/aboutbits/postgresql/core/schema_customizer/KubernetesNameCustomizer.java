package it.aboutbits.postgresql.core.schema_customizer;

import io.fabric8.crdv2.generator.v1.SchemaCustomizer;
import io.fabric8.kubernetes.api.model.apiextensions.v1.JSONSchemaProps;
import io.fabric8.kubernetes.client.utils.KubernetesSerialization;
import org.jspecify.annotations.NullMarked;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/// A [SchemaCustomizer.Customizer] that adds a Kubernetes name validation
/// `pattern` (RFC 1123 DNS label) to string properties in the generated CRD
/// JSON Schema.
///
/// The pattern enforces:
/// - Contain at most 63 characters
/// - Contain only lowercase alphanumeric characters or '-'
/// - Start with an alphabetic character
/// - End with an alphanumeric character
///
/// This customizer is intended to be used with the
/// [@SchemaCustomizer][SchemaCustomizer] annotation on a class whose string
/// properties represent Kubernetes resource names.
///
/// ### Behavior
///
/// - If `input` is **blank** (the default), the `"hostname"` format
///   is applied to **all** string properties of the annotated class.
/// - If `input` contains a **comma-separated list** of field names,
///   the format is applied **only** to the specified properties.
///
/// ### Usage examples
///
/// **Apply to all string properties:**
///
/// ```java
/// @SchemaCustomizer(KubernetesNameCustomizer.class)
/// public class SecretRef {
///     private String name = ""; // gets pattern: Kubernetes name regex
///     private String namespace; // gets pattern: Kubernetes name regex
/// }
/// ```
///
/// **Apply to specific properties only:**
///
/// ```java
/// @SchemaCustomizer(value = KubernetesNameCustomizer.class, input = "name,anotherName")
/// public class SecretRef {
///     private String name = "";        // gets pattern: Kubernetes name regex
///     private String anotherName = ""; // gets pattern: Kubernetes name regex
///     private String namespace;        // unchanged
/// }
/// ```
///
/// @see SchemaCustomizer
/// @see SchemaCustomizer.Customizer
@NullMarked
public class KubernetesNameCustomizer implements SchemaCustomizer.Customizer {
    static final String KUBERNETES_NAME_PATTERN = "^[a-z]([a-z0-9\\-]{0,61}[a-z0-9])?$";

    @Override
    public JSONSchemaProps apply(
            JSONSchemaProps jsonSchemaProps,
            String input,
            KubernetesSerialization kubernetesSerialization
    ) {
        var properties = jsonSchemaProps.getProperties();
        if (properties == null) {
            return jsonSchemaProps;
        }

        var targetFields = input.isBlank()
                ? Set.<String>of()
                : Arrays.stream(input.split(","))
                        .map(String::trim)
                        .collect(Collectors.toSet());

        for (var entry : properties.entrySet()) {
            var prop = entry.getValue();
            if ("string".equals(prop.getType())
                    && (targetFields.isEmpty() || targetFields.contains(entry.getKey()))
            ) {
                prop.setPattern(KUBERNETES_NAME_PATTERN);
            }
        }

        return jsonSchemaProps;
    }
}
