package it.aboutbits.postgresql.crd.grant;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import it.aboutbits.postgresql.core.BaseReconciler;
import it.aboutbits.postgresql.core.CRStatus;
import it.aboutbits.postgresql.crd.role.Role;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class GrantReconciler
        extends BaseReconciler<Role, CRStatus>
        implements Reconciler<Grant> {
    @Override
    public UpdateControl<Grant> reconcile(Grant resource, Context<Grant> context) {
        return UpdateControl.noUpdate();
    }

    @Override
    protected CRStatus newStatus() {
        return new CRStatus();
    }
}
