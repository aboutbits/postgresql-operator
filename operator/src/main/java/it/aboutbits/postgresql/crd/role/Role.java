package it.aboutbits.postgresql.crd.role;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.fabric8.crd.generator.annotation.AdditionalPrinterColumn;
import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Version;
import it.aboutbits.postgresql.core.CRStatus;
import it.aboutbits.postgresql.core.Named;
import org.jspecify.annotations.NullMarked;

@NullMarked
@Version("v1")
@Group("postgresql.aboutbits.it")
@AdditionalPrinterColumn(
        name = "Name",
        jsonPath = ".status.name",
        type = AdditionalPrinterColumn.Type.STRING
)
@AdditionalPrinterColumn(
        name = "Phase",
        jsonPath = ".status.phase",
        type = AdditionalPrinterColumn.Type.STRING
)
@AdditionalPrinterColumn(
        name = "Message",
        jsonPath = ".status.message",
        type = AdditionalPrinterColumn.Type.STRING
)
@AdditionalPrinterColumn(
        name = "Since",
        jsonPath = ".status.lastPhaseTransitionTime",
        type = AdditionalPrinterColumn.Type.DATE
)
@AdditionalPrinterColumn(
        name = "Age",
        jsonPath = ".metadata.creationTimestamp",
        type = AdditionalPrinterColumn.Type.DATE
)
public class Role
        extends CustomResource<RoleSpec, CRStatus>
        implements Namespaced, Named {
    @Override
    @JsonIgnore
    public String getName() {
        return getSpec().getName();
    }
}
