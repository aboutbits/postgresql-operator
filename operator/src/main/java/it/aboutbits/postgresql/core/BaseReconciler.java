package it.aboutbits.postgresql.core;

import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import it.aboutbits.postgresql.crd.clusterconnection.ClusterConnection;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@NullMarked
@Slf4j
public abstract class BaseReconciler<CR extends CustomResource<?, S> & Named, S extends CRStatus> {
    protected abstract S newStatus();

    public S initializeStatus(CR resource) {
        S status = resource.getStatus();

        //noinspection ConstantConditions
        if (status == null) {
            status = newStatus();
            resource.setStatus(status);
        }

        status.setName(resource.getName());
        status.setLastProbeTime(OffsetDateTime.now(ZoneOffset.UTC));
        status.setObservedGeneration(resource.getMetadata().getGeneration());

        return status;
    }

    public String getResourceNamespaceOrOwn(
            CR resource,
            @Nullable String resourceNamespace
    ) {
        if (resourceNamespace != null) {
            return resourceNamespace;
        }

        return resource.getMetadata().getNamespace();
    }

    public Optional<ClusterConnection> getReferencedClusterConnection(
            KubernetesClient kubernetesClient,
            CR resource,
            ClusterReference clusterRef
    ) {
        var connectionName = clusterRef.getName();
        var connectionNamespace = getResourceNamespaceOrOwn(resource, clusterRef.getNamespace());

        var clusterConnection = kubernetesClient.resources(ClusterConnection.class)
                .inNamespace(connectionNamespace)
                .withName(connectionName)
                .get();

        //noinspection ConstantConditions
        if (clusterConnection == null) {
            log.error(
                    "The specified ClusterConnection does not exist [resource={}/{}]",
                    connectionNamespace,
                    connectionName
            );

            return Optional.empty();
        }

        var currentPhase = clusterConnection.getStatus().getPhase();
        var expectedPhase = CRPhase.READY;

        if (!Objects.equals(currentPhase, expectedPhase)) {
            log.warn(
                    "The specified ClusterConnection is not ready yet [resource={}/{}]",
                    connectionNamespace,
                    connectionName
            );

            return Optional.empty();
        }

        return Optional.of(clusterConnection);
    }

    public <E extends Exception> UpdateControl<CR> handleError(
            CR resource,
            S status,
            E exception
    ) {
        log.error(
                "Failed to reconcile resource [resource={}]",
                resource.getMetadata().getName(),
                exception
        );

        status.setPhase(CRPhase.ERROR)
                .setMessage(exception.getMessage());

        return UpdateControl.patchStatus(resource)
                .rescheduleAfter(60, TimeUnit.SECONDS);
    }
}
