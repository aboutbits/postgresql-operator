package it.aboutbits.postgresql.crd.grant;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.fabric8.crd.generator.annotation.AdditionalPrinterColumn;
import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Version;
import it.aboutbits.postgresql.core.CRStatus;
import it.aboutbits.postgresql.core.Named;
import org.jspecify.annotations.NullMarked;

import java.util.Locale;
import java.util.stream.Collectors;

import static org.jooq.impl.DSL.grant;
import static org.jooq.impl.DSL.privilege;
import static org.jooq.impl.DSL.quotedName;
import static org.jooq.impl.DSL.role;

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
public class Grant
        extends CustomResource<GrantSpec, CRStatus>
        implements Namespaced, Named {
    @Override
    @JsonIgnore
    public String getName() {
        var spec = getSpec();

        var privileges = spec.getPrivileges()
                .stream()
                .map(privilege -> privilege(privilege.name().toLowerCase(Locale.ROOT)))
                .toList();

        var role = role(spec.getRole());
        var database = spec.getDatabase();
        var schema = spec.getSchema();

        var statements = spec.getObjects().stream()
                .map(object -> {
                    var on = quotedName(schema, object);

                    var statement = grant(privileges).on(on).to(role);

                    return statement.getSQL() + ";";
                })
                .collect(Collectors.joining("\n"));

        return """
               Statement(s) executed on database "%s":
               %s\
               """.formatted(database, statements);
    }
}
