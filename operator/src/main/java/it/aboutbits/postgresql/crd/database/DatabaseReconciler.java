package it.aboutbits.postgresql.crd.database;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Cleaner;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.DeleteControl;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import it.aboutbits.postgresql.core.BaseReconciler;
import it.aboutbits.postgresql.core.CRPhase;
import it.aboutbits.postgresql.core.CRStatus;
import it.aboutbits.postgresql.core.PostgreSQLContextFactory;
import it.aboutbits.postgresql.core.ReclaimPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jspecify.annotations.NullMarked;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

@NullMarked
@Slf4j
@RequiredArgsConstructor
public class DatabaseReconciler
        extends BaseReconciler<Database, CRStatus>
        implements Reconciler<Database>, Cleaner<Database> {
    private final DatabaseService databaseService;

    private final KubernetesClient kubernetesClient;
    private final PostgreSQLContextFactory contextFactory;

    @Override
    public UpdateControl<Database> reconcile(
            Database resource,
            Context<Database> context
    ) {
        var spec = resource.getSpec();
        var status = initializeStatus(resource);

        var name = resource.getMetadata().getName();
        var namespace = resource.getMetadata().getNamespace();

        log.info(
                "Reconciling Database [resource={}/{}, status.phase={}]",
                namespace,
                name,
                status.getPhase()
        );

        var clusterRef = spec.getClusterRef();

        var clusterConnectionOptional = getReferencedClusterConnection(
                kubernetesClient,
                resource,
                clusterRef
        );

        if (clusterConnectionOptional.isEmpty()) {
            status.setPhase(CRPhase.PENDING)
                    .setMessage("The specified ClusterConnection does not exist or is not ready yet [resource=%s/%s]".formatted(
                            getResourceNamespaceOrOwn(resource, clusterRef.getNamespace()),
                            clusterRef.getName()
                    ));

            return UpdateControl.patchStatus(resource)
                    .rescheduleAfter(60, TimeUnit.SECONDS);
        }

        var clusterConnection = clusterConnectionOptional.get();

        UpdateControl<Database> updateControl;

        try (var dsl = contextFactory.getDSLContext(clusterConnection)) {
            // PostgreSQL doesn't allow running `create database` in a transaction
            updateControl = reconcile(
                    dsl,
                    resource,
                    status
            );
        } catch (Exception e) {
            return handleError(
                    resource,
                    status,
                    e
            );
        }

        return updateControl;
    }

    @Override
    public DeleteControl cleanup(
            Database resource,
            Context<Database> context
    ) {
        var spec = resource.getSpec();
        var status = initializeStatus(resource);

        var name = resource.getMetadata().getName();
        var namespace = resource.getMetadata().getNamespace();

        log.info(
                "{}ing Database [resource={}/{}, spec.name={}, status.phase={}]",
                spec.getReclaimPolicy().toValue(),
                namespace,
                name,
                spec.getName(),
                status.getPhase()
        );

        if (status.getPhase() != CRPhase.DELETING) {
            status.setPhase(CRPhase.DELETING);

            if (spec.getReclaimPolicy() == ReclaimPolicy.DELETE) {
                status.setMessage("Database deletion in progress");
            }

            context.getClient().resource(resource).patchStatus();

            return DeleteControl.noFinalizerRemoval()
                    .rescheduleAfter(0, TimeUnit.SECONDS);
        }

        // We do not actually delete the database if the reclaimPolicy is set to RETAIN, we only delete the CR instance
        if (spec.getReclaimPolicy() == ReclaimPolicy.RETAIN) {
            return DeleteControl.defaultDelete();
        }

        var clusterRef = spec.getClusterRef();

        var clusterConnectionOptional = getReferencedClusterConnection(
                kubernetesClient,
                resource,
                clusterRef
        );

        if (clusterConnectionOptional.isEmpty()) {
            status.setMessage("The specified ClusterConnection no longer exists or is not ready yet [resource=%s/%s]".formatted(
                    getResourceNamespaceOrOwn(resource, clusterRef.getNamespace()),
                    clusterRef.getName()
            ));

            context.getClient().resource(resource).patchStatus();

            return DeleteControl.noFinalizerRemoval()
                    .rescheduleAfter(60, TimeUnit.SECONDS);
        }

        var clusterConnection = clusterConnectionOptional.get();

        try (var dsl = contextFactory.getDSLContext(clusterConnection)) {
            databaseService.dropDatabase(dsl, spec);

            return DeleteControl.defaultDelete();
        } catch (Exception e) {
            log.error(
                    "Failed to delete Database [resource=%s/%s, spec.name=%s, status.phase=%s]".formatted(
                            namespace,
                            name,
                            spec.getName(),
                            status.getPhase()
                    ),
                    e
            );

            status.setMessage("Deletion failed: %s".formatted(e.getMessage()));

            context.getClient().resource(resource).patchStatus();

            return DeleteControl.noFinalizerRemoval()
                    .rescheduleAfter(60, TimeUnit.SECONDS);
        }
    }

    @Override
    protected CRStatus newStatus() {
        return new CRStatus();
    }

    private UpdateControl<Database> reconcile(
            DSLContext dsl,
            Database resource,
            CRStatus status
    ) {
        var name = resource.getMetadata().getName();
        var namespace = resource.getMetadata().getNamespace();

        var spec = resource.getSpec();

        // Create and return the database if it doesn't exist yet
        if (!databaseService.databaseExists(dsl, spec)) {
            log.info(
                    "Creating Database [resource={}/{}]",
                    namespace,
                    name
            );

            databaseService.createDatabase(
                    dsl,
                    spec
            );

            status.setPhase(CRPhase.READY)
                    .setMessage(null);

            return UpdateControl.patchStatus(resource);
        }

        var currentOwner = databaseService.fetchDatabaseOwner(dsl, spec);
        var expectedOwner = spec.getOwner();

        if (Objects.equals(currentOwner, expectedOwner)) {
            log.info(
                    "Database up-to-date [resource={}/{}]",
                    namespace,
                    name
            );

            return UpdateControl.noUpdate();
        }

        log.info(
                "Changing Database owner [resource={}/{}]",
                namespace,
                name
        );

        databaseService.changeDatabaseOwner(dsl, spec);

        status.setPhase(CRPhase.READY)
                .setMessage("Database owner changed [previousOwner=%s, newOwner=%s]".formatted(
                        currentOwner,
                        expectedOwner
                ));

        return UpdateControl.patchStatus(resource);
    }
}
