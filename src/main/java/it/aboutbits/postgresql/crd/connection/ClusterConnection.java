package it.aboutbits.postgresql.crd.connection;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Version;
import it.aboutbits.postgresql.core.CRStatus;
import it.aboutbits.postgresql.core.Named;
import org.jspecify.annotations.NullMarked;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.StringJoiner;

@NullMarked
@Version("v1")
@Group("postgresql.aboutbits.it")
public class ClusterConnection
        extends CustomResource<ClusterConnectionSpec, CRStatus>
        implements Namespaced, Named {
    @Override
    @JsonIgnore
    public String getName() {
        var spec = getSpec();

        var jdbcUrl = "jdbc:postgresql://%s:%d/%s".formatted(
                spec.getHost(),
                spec.getPort(),
                spec.getMaintenanceDatabase()
        );

        var stringJoiner = new StringJoiner("&", jdbcUrl + "?", "");

        spec.getParameters().forEach((key, value) ->
                stringJoiner.add("%s=%s".formatted(
                        URLEncoder.encode(key, StandardCharsets.UTF_8)
                                .replace("+", "%20"),
                        URLEncoder.encode(value, StandardCharsets.UTF_8)
                                .replace("+", "%20")
                ))
        );

        return stringJoiner.toString();
    }
}
