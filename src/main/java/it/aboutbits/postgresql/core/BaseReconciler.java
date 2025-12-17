package it.aboutbits.postgresql.core;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import it.aboutbits.postgresql.crd.connection.ClusterConnection;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Optional;

@NullMarked
@Slf4j
public abstract class BaseReconciler<CR extends CustomResource<?, S> & Named, S extends CRStatus> {
    @NonNull
    protected abstract S newStatus();

    public S initializeStatus(CR resource) {
        S status = resource.getStatus();

        if (status == null) {
            status = newStatus();

            status.setName(resource.getName());

            resource.setStatus(status);
        }

        status.setLastProbeTime(OffsetDateTime.now(ZoneOffset.UTC));
        status.setObservedGeneration(resource.getMetadata().getGeneration());

        return status;
    }

    public String getResourceNamespaceOrOwn(
            @Nullable String resourceNamespace
    ) {
        if (resourceNamespace != null) {
            return resourceNamespace;
        }

        return ((HasMetadata) this).getMetadata().getNamespace();
    }

    public Optional<ClusterConnection> getReferencedClusterConnection(
            KubernetesClient kubernetesClient,
            ClusterReference clusterRef
    ) {
        var connectionName = clusterRef.getName();
        var connectionNamespace = getResourceNamespaceOrOwn(clusterRef.getNamespace());

        var clusterConnection = kubernetesClient.resources(ClusterConnection.class)
                .inNamespace(connectionNamespace)
                .withName(connectionName)
                .get();

        if (clusterConnection == null) {
            log.error(
                    "The specified ClusterConnection does not exist [clusterConnection={}/{}]",
                    connectionNamespace,
                    connectionName
            );

            return Optional.empty();
        }

        var currentPhase = clusterConnection.getStatus().getPhase();
        var expectedPhase = CRPhase.READY;

        if (!Objects.equals(currentPhase, expectedPhase)) {
            log.warn(
                    "The specified ClusterConnection is not ready yet [clusterConnection={}/{}]",
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
        return handleError(
                resource,
                status,
                exception.getMessage()
        );
    }

    public UpdateControl<CR> handleError(
            CR resource,
            S status,
            @Nullable String message
    ) {
        status.setPhase(CRPhase.ERROR).setMessage(message);

        return UpdateControl.patchStatus(resource)
                .rescheduleAfter(Duration.ofSeconds(30));
    }
}
