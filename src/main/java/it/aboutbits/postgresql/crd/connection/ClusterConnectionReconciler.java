package it.aboutbits.postgresql.crd.connection;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import it.aboutbits.postgresql.core.BaseReconciler;
import it.aboutbits.postgresql.core.CRPhase;
import it.aboutbits.postgresql.core.CRStatus;
import it.aboutbits.postgresql.core.PostgreSQLContextFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;

@NullMarked
@Slf4j
@RequiredArgsConstructor
public class ClusterConnectionReconciler
        extends BaseReconciler<ClusterConnection, CRStatus>
        implements Reconciler<ClusterConnection> {
    private final PostgreSQLContextFactory contextFactory;

    @Override
    public UpdateControl<ClusterConnection> reconcile(
            ClusterConnection resource,
            Context<ClusterConnection> context
    ) {
        var status = initializeStatus(resource);

        try (var dsl = contextFactory.getDSLContext(resource)) {
            var version = dsl.fetchSingle("select version()").into(String.class);

            status.setPhase(CRPhase.READY).setMessage(version);

            return UpdateControl.patchStatus(resource);
        } catch (Exception e) {
            log.error("Failed to check database connectivity", e);

            return handleError(
                    resource,
                    status,
                    e
            );
        }
    }

    @Override
    protected CRStatus newStatus() {
        return new CRStatus();
    }
}
