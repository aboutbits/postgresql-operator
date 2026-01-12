package it.aboutbits.postgresql.crd.clusterconnection;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import it.aboutbits.postgresql.core.PostgreSQLContextFactory;
import jakarta.inject.Inject;
import org.jooq.CloseableDSLContext;
import org.jooq.exception.DataAccessException;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@NullMarked
@QuarkusTest
class ClusterConnectionReconcilerErrorTest {
    @SuppressWarnings("NullAway.Init")
    @InjectMock
    PostgreSQLContextFactory contextFactory;

    @Inject
    ClusterConnectionReconciler reconciler;

    private ClusterConnection resource;
    private Context<ClusterConnection> context;

    @BeforeEach
    void setUp() {
        resource = new ClusterConnection();

        var metadata = new ObjectMeta();
        metadata.setGeneration(1L);

        // We mock the spec to ensure getName() works safely without throwing NPE,
        // as the custom getName() implementation in ClusterConnection relies on spec fields.
        var spec = mock(ClusterConnectionSpec.class);

        when(spec.getHost()).thenReturn("localhost");
        when(spec.getPort()).thenReturn(5432);
        when(spec.getMaintenanceDatabase()).thenReturn("postgres");
        when(spec.getParameters()).thenReturn(Collections.emptyMap());

        resource.setSpec(spec);
        resource.setMetadata(metadata);

        //noinspection unchecked
        context = mock(Context.class);
    }

    @Test
    @DisplayName("Should handle SQLException during DSL context creation")
    void reconcile_whenDslCreationFails_shouldReturnErrorStatus() {
        // given
        var errorMessage = "Connection refused to database";

        when(contextFactory.getDSLContext(resource)).thenThrow(
                new RuntimeException(errorMessage)
        );

        // when
        var updateControl = reconciler.reconcile(resource, context);

        // then
        assertThat(updateControl.getResource())
                .isPresent()
                .get()
                .extracting(ClusterConnection::getStatus)
                .satisfies(status ->
                        assertThat(status.getMessage()).contains(errorMessage)
                );
    }

    @Test
    @DisplayName("Should handle DataAccessException during version check")
    void reconcile_whenVersionQueryFails_shouldReturnErrorStatus() {
        // given
        var errorMessage = "Query execution failed";
        var dslContext = mock(CloseableDSLContext.class);

        when(contextFactory.getDSLContext(resource)).thenReturn(
                dslContext
        );

        when(dslContext.fetchSingle(anyString())).thenThrow(
                new DataAccessException(errorMessage)
        );

        // when
        var updateControl = reconciler.reconcile(resource, context);

        // then
        assertThat(updateControl.getResource())
                .isPresent()
                .get()
                .extracting(ClusterConnection::getStatus)
                .satisfies(status ->
                        assertThat(status.getMessage()).contains(errorMessage)
                );
    }
}
