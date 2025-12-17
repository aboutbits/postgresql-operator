package it.aboutbits.postgresql.crd.connection;

import io.fabric8.generator.annotation.Required;
import it.aboutbits.postgresql.core.SecretRef;
import lombok.Getter;
import lombok.Setter;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

@NullMarked
@Getter
@Setter
public class ClusterConnectionSpec {
    @Required
    private String host = "";

    @Required
    private Integer port = -1;

    @Required
    private String maintenanceDatabase = "postgres";

    @Required
    private SecretRef adminSecretRef = new SecretRef();

    @io.fabric8.generator.annotation.Nullable
    private Map<String, String> parameters = new HashMap<>();
}
