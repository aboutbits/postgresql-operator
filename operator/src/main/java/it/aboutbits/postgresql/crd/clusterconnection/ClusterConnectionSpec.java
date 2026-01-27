package it.aboutbits.postgresql.crd.clusterconnection;

import io.fabric8.generator.annotation.Max;
import io.fabric8.generator.annotation.Min;
import io.fabric8.generator.annotation.Required;
import io.fabric8.generator.annotation.ValidationRule;
import it.aboutbits.postgresql.core.SecretRef;
import lombok.Getter;
import lombok.Setter;
import org.jspecify.annotations.NullMarked;

import java.util.HashMap;
import java.util.Map;

@NullMarked
@Getter
@Setter
public class ClusterConnectionSpec {
    @Required
    @ValidationRule(
            value = "self.trim().size() > 0",
            message = "The ClusterConnection host must not be empty."
    )
    private String host = "";

    @Required
    @Min(1)
    @Max(65535)
    private int port = -1;

    @Required
    @ValidationRule(
            value = "self.trim().size() > 0",
            message = "The ClusterConnection database must not be empty."
    )
    private String database = "postgres";

    @Required
    private SecretRef adminSecretRef = new SecretRef();

    @io.fabric8.generator.annotation.Nullable
    private Map<String, String> parameters = new HashMap<>();
}
