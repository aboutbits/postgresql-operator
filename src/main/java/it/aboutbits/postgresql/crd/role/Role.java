package it.aboutbits.postgresql.crd.role;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
public class Role
        extends CustomResource<RoleSpec, CRStatus>
        implements Namespaced, Named {
    @Override
    @JsonIgnore
    public String getName() {
        return getSpec().getName();
    }
}
