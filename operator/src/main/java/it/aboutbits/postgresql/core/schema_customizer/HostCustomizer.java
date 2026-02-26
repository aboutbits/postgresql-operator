package it.aboutbits.postgresql.core.schema_customizer;

import io.fabric8.crdv2.generator.v1.SchemaCustomizer;
import io.fabric8.kubernetes.api.model.apiextensions.v1.JSONSchemaProps;
import io.fabric8.kubernetes.client.utils.KubernetesSerialization;
import org.jspecify.annotations.NullMarked;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/// A [SchemaCustomizer.Customizer] that sets the `format` of string properties
/// to `{"anyOf":[{"format":"hostname"},{"format":"ipv4"},{"format":"ipv6"}]}`
/// in the generated CRD JSON Schema.
///
/// This customizer is intended to be used with the
/// [@SchemaCustomizer][SchemaCustomizer] annotation on a class whose properties
/// should be validated to valid hosts defined.
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
/// @SchemaCustomizer(value = HostCustomizer.class)
/// public class ClusterConnectionSpec {
///     private String host = "";        // gets custom format
///     private String anotherHost = ""; // gets custom format
/// }
/// ```
///
/// **Apply to specific properties only:**
///
/// ```java
/// @SchemaCustomizer(value = HostCustomizer.class, input = "host,anotherHost")
/// public class ClusterConnectionSpec {
///     private String host = "";          // gets custom format
///     private String anotherHost = "";   // gets custom format
///     private String unchangedHost = ""; // unchanged
/// }
/// ```
///
/// @see SchemaCustomizer
/// @see SchemaCustomizer.Customizer
@NullMarked
public class HostCustomizer implements SchemaCustomizer.Customizer {
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
                prop.setFormat(null);

                var hostnameProp = new JSONSchemaProps();
                hostnameProp.setFormat("hostname");

                var ipv4Prop = new JSONSchemaProps();
                ipv4Prop.setFormat("ipv4");

                var ipv6Prop = new JSONSchemaProps();
                ipv6Prop.setFormat("ipv6");

                prop.setAnyOf(List.of(
                        hostnameProp,
                        ipv4Prop,
                        ipv6Prop
                ));
            }
        }

        return jsonSchemaProps;
    }
}
