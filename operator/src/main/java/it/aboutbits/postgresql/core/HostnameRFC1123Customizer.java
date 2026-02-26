package it.aboutbits.postgresql.core;

import io.fabric8.crdv2.generator.v1.SchemaCustomizer;
import io.fabric8.kubernetes.api.model.apiextensions.v1.JSONSchemaProps;
import io.fabric8.kubernetes.client.utils.KubernetesSerialization;
import org.jspecify.annotations.NullMarked;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/// A [SchemaCustomizer.Customizer] that sets the `format` of string properties
/// to `"hostname"` (RFC 1123) in the generated CRD JSON Schema.
///
/// This customizer is intended to be used with the
/// [@SchemaCustomizer][SchemaCustomizer] annotation on a class whose properties
/// should be validated as RFC 1123 hostnames by the Kubernetes API server.
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
/// @SchemaCustomizer(HostnameRFC1123Customizer.class)
/// public class SecretRef {
///     private String name = ""; // gets format: "hostname"
///     private String namespace; // gets format: "hostname"
/// }
/// ```
///
/// **Apply to specific properties only:**
///
/// ```java
/// @SchemaCustomizer(value = HostnameRFC1123Customizer.class, input = "host")
/// public class ClusterConnectionSpec {
///     private String host = "";        // gets format: "hostname"
///     private String anotherHost = ""; // gets format: "hostname"
///     private String database = "";    // unchanged
/// }
/// ```
///
/// @see SchemaCustomizer
/// @see SchemaCustomizer.Customizer
@NullMarked
public class HostnameRFC1123Customizer implements SchemaCustomizer.Customizer {
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
            if ("string".equals(prop.getType())) {
                if (targetFields.isEmpty() || targetFields.contains(entry.getKey())) {
                    prop.setFormat("hostname");
                }
            }
        }

        return jsonSchemaProps;
    }
}
