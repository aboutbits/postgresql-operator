package it.aboutbits.postgresql.crd.clusterconnection;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.quarkus.test.junit.QuarkusTest;
import it.aboutbits.postgresql._support.testdata.base.TestUtil;
import it.aboutbits.postgresql._support.testdata.persisted.Given;
import it.aboutbits.postgresql._support.valuesource.BlankSource;
import it.aboutbits.postgresql.core.CRPhase;
import it.aboutbits.postgresql.core.CRStatus;
import it.aboutbits.postgresql.core.FileRef;
import it.aboutbits.postgresql.core.PostgreSQLContextFactory;
import lombok.RequiredArgsConstructor;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jooq.DSLContext;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;

import java.io.IOException;
import java.nio.file.Files;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

@QuarkusTest
@RequiredArgsConstructor
@NullMarked
class ClusterConnectionReconcilerTest {
    private final Given given;

    private final PostgreSQLContextFactory postgreSQLContextFactory;

    private final KubernetesClient kubernetesClient;

    @SuppressWarnings("NullAway.Init")
    @ConfigProperty(name = "quarkus.datasource.devservices.username")
    String dbUsername;

    @SuppressWarnings("NullAway.Init")
    @ConfigProperty(name = "quarkus.datasource.devservices.password")
    String dbPassword;

    @BeforeEach
    void resetEnvironment() {
        TestUtil.resetEnvironment(kubernetesClient);
    }

    @Nested
    class CRDValidation {
        @Test
        @DisplayName("when both adminSecretRef and adminSecretFileRef set, should reject")
        void whenBothSet_shouldReject() {
            var fileRef = new FileRef();
            fileRef.setPath("/mnt/secrets/db-credentials.json");

            assertThatThrownBy(() -> given.one()
                    .clusterConnection()
                    .withAdminSecretFileRef(fileRef)
                    .returnFirst()
            ).isInstanceOf(KubernetesClientException.class)
                    .hasMessageContaining("Exactly one of");
        }

        @Test
        @DisplayName("when neither adminSecretRef nor adminSecretFileRef set, should reject")
        void whenNeitherSet_shouldReject() {
            assertThatThrownBy(() -> given.one()
                    .clusterConnection()
                    .withoutAdminSecret()
                    .returnFirst()
            ).isInstanceOf(KubernetesClientException.class)
                    .hasMessageContaining("Exactly one of");
        }

        @ParameterizedTest
        @BlankSource
        @DisplayName("when adminSecretFileRef has blank path, should reject")
        void whenFileRefBlankPath_shouldReject(String blankOrEmptyString) {
            var fileRef = new FileRef();
            fileRef.setPath(blankOrEmptyString);

            assertThatThrownBy(() -> given.one()
                    .clusterConnection()
                    .withoutAdminSecret()
                    .withAdminSecretFileRef(fileRef)
                    .returnFirst()
            ).isInstanceOf(KubernetesClientException.class)
                    .hasMessageContaining("must not be empty");
        }
    }

    @Test
    @DisplayName("When a ClusterConnection is created, the status should be ready")
    void createsCustomResource_andReconcilerStatusIsReady() {
        // given / when
        var customResource = given.one()
                .clusterConnection()
                .withName("test-connection")
                .returnFirst();

        // then
        AtomicReference<@Nullable DSLContext> dslAtomic = new AtomicReference<>();
        assertThatNoException().isThrownBy(
                () -> dslAtomic.set(postgreSQLContextFactory.getDSLContext(customResource))
        );

        var dsl = Objects.requireNonNull(dslAtomic.get());

        var version = dsl.fetchSingle("select version()").into(String.class);

        var expectedStatus = getInitialClusterConnectionStatus(customResource);
        expectedStatus.setMessage(version);

        assertThatClusterConnectionHasExpectedStatus(
                customResource,
                expectedStatus,
                OffsetDateTime.now(ZoneOffset.UTC)
        );
    }

    @Test
    @DisplayName("When a ClusterConnection with adminSecretFileRef is created, the status should be ready")
    void createsCustomResourceWithFileRef_andReconcilerStatusIsReady() throws IOException {
        // given
        var credentialsFile = Files.createTempFile("db-credentials", ".json");
        try {
            Files.writeString(
                    credentialsFile,
                    """
                    {"username": "%s", "password": "%s"}
                    """.formatted(dbUsername, dbPassword)
            );

            var fileRef = new FileRef();
            fileRef.setPath(credentialsFile.toAbsolutePath().toString());

            // when
            var customResource = given.one()
                    .clusterConnection()
                    .withName("test-connection-file-ref")
                    .withoutAdminSecret()
                    .withAdminSecretFileRef(fileRef)
                    .returnFirst();

            // then
            AtomicReference<@Nullable DSLContext> dslAtomic = new AtomicReference<>();
            assertThatNoException().isThrownBy(
                    () -> dslAtomic.set(postgreSQLContextFactory.getDSLContext(customResource))
            );

            var dsl = Objects.requireNonNull(dslAtomic.get());

            var version = dsl.fetchSingle("select version()").into(String.class);

            var expectedStatus = getInitialClusterConnectionStatus(customResource);
            expectedStatus.setMessage(version);

            assertThatClusterConnectionHasExpectedStatus(
                    customResource,
                    expectedStatus,
                    OffsetDateTime.now(ZoneOffset.UTC)
            );
        } finally {
            Files.deleteIfExists(credentialsFile);
        }
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
                            within(5, ChronoUnit.SECONDS)
                    );
                    assertThat(status.getLastPhaseTransitionTime()).isCloseTo(
                            now,
                            within(5, ChronoUnit.SECONDS)
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
