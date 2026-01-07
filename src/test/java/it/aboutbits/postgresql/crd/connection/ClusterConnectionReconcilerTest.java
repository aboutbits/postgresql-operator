package it.aboutbits.postgresql.crd.connection;

import io.quarkus.test.junit.QuarkusTest;
import it.aboutbits.postgresql._support.testdata.persisted.Given;
import it.aboutbits.postgresql.core.CRPhase;
import it.aboutbits.postgresql.core.CRStatus;
import it.aboutbits.postgresql.core.PostgreSQLContextFactory;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.within;

@QuarkusTest
@RequiredArgsConstructor
class ClusterConnectionReconcilerTest {
    private final Given given;

    private final PostgreSQLContextFactory postgreSQLContextFactory;

    @Test
    @DisplayName("When a ClusterConnection is created, the status should be ready")
    void createsCustomResource_andReconcilerStatusIsReady() {
        // given / when
        var customResource = given.one()
                .clusterConnection()
                .withName("test-connection")
                .returnFirst();

        // then
        AtomicReference<DSLContext> dsl = new AtomicReference<>();
        assertThatNoException().isThrownBy(
                () -> dsl.set(postgreSQLContextFactory.getDSLContext(customResource))
        );

        var version = dsl.get().fetchSingle("select version()").into(String.class);

        var expectedStatus = getInitialClusterConnectionStatus(customResource);
        expectedStatus.setMessage(version);

        assertThatClusterConnectionHasExpectedStatus(
                customResource,
                expectedStatus,
                OffsetDateTime.now(ZoneOffset.UTC)
        );
    }

    private static void assertThatClusterConnectionHasExpectedStatus(
            ClusterConnection clusterConnection,
            CRStatus expectedStatus,
            OffsetDateTime now
    ) {
        assertThat(clusterConnection)
                .isNotNull()
                .extracting(ClusterConnection::getStatus)
                .satisfies(status -> {
                    assertThat(status.getLastProbeTime()).isCloseTo(
                            now,
                            within(10, ChronoUnit.SECONDS)
                    );
                    assertThat(status.getLastPhaseTransitionTime()).isCloseTo(
                            now,
                            within(10, ChronoUnit.SECONDS)
                    );
                })
                .usingRecursiveComparison()
                .ignoringFields("lastProbeTime", "lastPhaseTransitionTime")
                .isEqualTo(expectedStatus);
    }

    private static CRStatus getInitialClusterConnectionStatus(ClusterConnection clusterConnection) {
        return new CRStatus()
                .setName(clusterConnection.getName())
                .setPhase(CRPhase.READY)
                .setObservedGeneration(1L);
    }
}
