package it.aboutbits.postgresql.crd.schema;

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
public class SchemaReconciler
        extends BaseReconciler<Schema, CRStatus>
        implements Reconciler<Schema>, Cleaner<Schema> {
    private final SchemaService schemaService;

    private final KubernetesClient kubernetesClient;
    private final PostgreSQLContextFactory contextFactory;

    @Override
    public UpdateControl<Schema> reconcile(
            Schema resource,
            Context<Schema> context
    ) {
        var spec = resource.getSpec();
        var status = initializeStatus(resource);

        var name = resource.getMetadata().getName();
        var namespace = resource.getMetadata().getNamespace();

        log.info(
                "Reconciling Schema [resource={}/{}, status.phase={}]",
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

        var database = spec.getDatabase();
        var clusterConnection = clusterConnectionOptional.get();

        UpdateControl<Schema> updateControl;

        try (var dsl = contextFactory.getDSLContext(clusterConnection, database)) {
            // Run everything in a single transaction
            updateControl = dsl.transactionResult(
                    cfg -> reconcileInTransaction(
                            cfg.dsl(),
                            resource,
                            status
                    )
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
            Schema resource,
            Context<Schema> context
    ) {
        var spec = resource.getSpec();
        var status = initializeStatus(resource);

        var name = resource.getMetadata().getName();
        var namespace = resource.getMetadata().getNamespace();

        log.info(
                "{}ing Schema [resource={}/{}, spec.name={}, status.phase={}]",
                spec.getReclaimPolicy().toValue(),
                namespace,
                name,
                spec.getName(),
                status.getPhase()
        );

        if (status.getPhase() != CRPhase.DELETING) {
            status.setPhase(CRPhase.DELETING);

            if (spec.getReclaimPolicy() == ReclaimPolicy.DELETE) {
                status.setMessage("Schema deletion in progress");
            }

            context.getClient().resource(resource).patchStatus();

            return DeleteControl.noFinalizerRemoval()
                    .rescheduleAfter(100, TimeUnit.MILLISECONDS);
        }

        // We do not actually delete the schema if the reclaimPolicy is set to RETAIN, we only delete the CR instance
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

        var database = spec.getDatabase();
        var clusterConnection = clusterConnectionOptional.get();

        try (var dsl = contextFactory.getDSLContext(clusterConnection, database)) {
            schemaService.dropSchema(dsl, spec);

            return DeleteControl.defaultDelete();
        } catch (Exception e) {
            log.error(
                    "Failed to delete Schema [resource=%s/%s, spec.name=%s, status.phase=%s]".formatted(
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

    private UpdateControl<Schema> reconcileInTransaction(
            DSLContext tx,
            Schema resource,
            CRStatus status
    ) {
        var name = resource.getMetadata().getName();
        var namespace = resource.getMetadata().getNamespace();

        var spec = resource.getSpec();

        // Create and return the schema if it doesn't exist yet
        if (!schemaService.schemaExists(tx, spec)) {
            log.info(
                    "Creating Schema [resource={}/{}]",
                    namespace,
                    name
            );

            schemaService.createSchema(
                    tx,
                    spec
            );

            status.setPhase(CRPhase.READY)
                    .setMessage(null);

            return UpdateControl.patchStatus(resource);
        }

        var currentOwner = schemaService.fetchSchemaOwner(tx, spec);
        var expectedOwner = spec.getOwner();

        if (Objects.equals(currentOwner, expectedOwner)) {
            log.info(
                    "Schema up-to-date [resource={}/{}]",
                    namespace,
                    name
            );

            return UpdateControl.noUpdate();
        }

        log.info(
                "Changing Schema owner [resource={}/{}]",
                namespace,
                name
        );

        schemaService.changeSchemaOwner(tx, spec);

        status.setPhase(CRPhase.READY)
                .setMessage("Schema owner changed [previousOwner=%s, newOwner=%s]".formatted(
                        currentOwner,
                        expectedOwner
                ));

        return UpdateControl.patchStatus(resource);
    }
}
