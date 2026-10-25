package it.aboutbits.postgresql.core;

import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import it.aboutbits.postgresql.crd.clusterconnection.ClusterConnection;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@NullMarked
public abstract class BaseReconciler<CR extends CustomResource<?, S> & Named, S extends CRStatus> {
    protected abstract S newStatus();

    public S initializeStatus(CR resource) {
        var status = resource.getStatus();

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
            ResourceRef clusterRef
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

    /// Sets the phase to [CRPhase#ERROR] for a failure without an exception, and logs the message.
    public void setErrorPhase(
            CR resource,
            S status,
            String message
    ) {
        log.warn(
                "Failed to reconcile {} [resource={}/{}, message={}]",
                resource.getKind(),
                resource.getMetadata().getNamespace(),
                resource.getMetadata().getName(),
                message
        );

        status.updatePhase(CRPhase.ERROR)
                .setMessage(message);
    }

    public <E extends Exception> UpdateControl<CR> handleError(
            CR resource,
            S status,
            E exception
    ) {
        log.error(
                "Failed to reconcile {} [resource={}/{}]",
                resource.getKind(),
                resource.getMetadata().getNamespace(),
                resource.getMetadata().getName(),
                exception
        );

        status.updatePhase(CRPhase.ERROR)
                .setMessage(exception.getMessage());

        return UpdateControl.patchStatus(resource)
                .rescheduleAfter(Duration.ofSeconds(60));
    }
}
