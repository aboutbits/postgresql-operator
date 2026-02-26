package it.aboutbits.postgresql.core;

import io.fabric8.crdv2.generator.v1.SchemaCustomizer;
import io.fabric8.generator.annotation.Required;
import io.fabric8.generator.annotation.ValidationRule;
import it.aboutbits.postgresql.core.schema_customizer.KubernetesNameCustomizer;
import lombok.Getter;
import lombok.Setter;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/// A reference to a Kubernetes resource identified by [#namespace] (optional) and [#name].
///
/// This class is used wherever a CRD spec needs to point to another Kubernetes resource.
///
/// ### Namespace resolution
///
/// The [#namespace] field is **nullable**. When it is `null` (or omitted
/// in the CR manifest), the operator resolves the target resource in the
/// **same namespace as the CR that contains the reference**. This convention
/// keeps single-namespace deployments simple — users only need to set
/// `namespace` when referring to a resource in a *different* namespace.
///
/// | `namespace` value | Resolved namespace                                  |
/// |-------------------|-----------------------------------------------------|
/// | non-null          | the explicit namespace                              |
/// | `null` (omitted)  | the namespace of the CR that owns this reference    |
@Getter
@Setter
@SchemaCustomizer(KubernetesNameCustomizer.class)
@NullMarked
public class ResourceRef {
    /// The namespace of the referenced Kubernetes resource.
    /// If `null`, defaults to the namespace of the CR that defines this reference.
    @io.fabric8.generator.annotation.Nullable
    private @Nullable String namespace;

    @Required
    @ValidationRule(
            value = "self.trim().size() > 0",
            message = "The name must not be empty."
    )
    private String name = "";
}
