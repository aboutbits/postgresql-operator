package it.aboutbits.postgresql._support.testdata.base;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import it.aboutbits.postgresql.crd.clusterconnection.ClusterConnection;
import it.aboutbits.postgresql.crd.database.Database;
import it.aboutbits.postgresql.crd.defaultprivilege.DefaultPrivilege;
import it.aboutbits.postgresql.crd.grant.Grant;
import it.aboutbits.postgresql.crd.role.Role;
import it.aboutbits.postgresql.crd.schema.Schema;
import org.jspecify.annotations.NullMarked;

import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;

@NullMarked
public class TestUtil {
    public static void resetEnvironment(KubernetesClient kubernetesClient) {
        // Reverse Dependency Deletion
        deleteResource(kubernetesClient, DefaultPrivilege.class);
        deleteResource(kubernetesClient, Grant.class);
        deleteResource(kubernetesClient, Schema.class);
        deleteResource(kubernetesClient, Role.class);
        deleteResource(kubernetesClient, Database.class);
        deleteResource(kubernetesClient, ClusterConnection.class);
    }

    public static void deleteResource(
            KubernetesClient kubernetesClient,
            Class<? extends HasMetadata> resourceClass
    ) {
        // Async call to the Kubernetes API Server that triggers the Operator Controllers
        kubernetesClient.resources(resourceClass).delete();

        // Wait for the Operator Controller to finish the CR cleanup
        await().atMost(5, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .until(() -> kubernetesClient.resources(resourceClass).list().getItems().isEmpty());
    }
}
