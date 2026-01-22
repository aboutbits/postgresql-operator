package it.aboutbits.postgresql.crd.defaultprivilege;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.quarkus.test.junit.QuarkusTest;
import it.aboutbits.postgresql._support.testdata.persisted.Given;
import it.aboutbits.postgresql._support.valuesource.BlankSource;
import it.aboutbits.postgresql.core.CRPhase;
import it.aboutbits.postgresql.core.CRStatus;
import it.aboutbits.postgresql.crd.clusterconnection.ClusterConnection;
import it.aboutbits.postgresql.crd.database.Database;
import it.aboutbits.postgresql.crd.role.Role;
import it.aboutbits.postgresql.crd.schema.Schema;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static it.aboutbits.postgresql.core.Privilege.SELECT;
import static it.aboutbits.postgresql.core.Privilege.USAGE;
import static it.aboutbits.postgresql.crd.defaultprivilege.DefaultPrivilegeObjectType.SCHEMA;
import static it.aboutbits.postgresql.crd.defaultprivilege.DefaultPrivilegeObjectType.SEQUENCE;
import static it.aboutbits.postgresql.crd.defaultprivilege.DefaultPrivilegeObjectType.TABLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@NullMarked
@QuarkusTest
@RequiredArgsConstructor
class DefaultPrivilegeReconcilerTest {
    private final Given given;
    private final KubernetesClient kubernetesClient;

    @BeforeEach
    void resetEnvironment() {
        deleteResources(DefaultPrivilege.class);
        deleteResources(Schema.class);
        deleteResources(Database.class);
        deleteResources(Role.class);
        deleteResources(ClusterConnection.class);

        // Create the default connection "test-cluster-connection" used by GrantCreate defaults
        given.one().clusterConnection()
                .withName("test-cluster-connection")
                .returnFirst();
    }

    private <T extends HasMetadata> void deleteResources(Class<T> resourceClass) {
        kubernetesClient.resources(resourceClass)
                .withTimeout(5, TimeUnit.SECONDS)
                .delete();
    }

    @Nested
    class CRDValidation {
        @Nested
        class FieldSize {
            @ParameterizedTest
            @BlankSource
            @DisplayName("Should fail when the database is a blank or empty String (CEL rule)")
            void failWhenDatabaseIsBlankOrEmptyString(
                    String blankOrEmptyString
            ) {
                // then
                assertThatThrownBy(() ->
                        // given / when
                        given.one()
                                .defaultPrivilege()
                                .withDatabase(blankOrEmptyString)
                                .withObjectType(SCHEMA)
                                .withPrivileges(USAGE)
                                .apply()
                ).isInstanceOf(KubernetesClientException.class)
                        .hasMessageContaining("The DefaultPrivilege database must not be empty.");
            }

            @ParameterizedTest
            @BlankSource
            @DisplayName("Should fail when the role is a blank or empty String (CEL rule)")
            void failWhenRoleIsBlankOrEmptyString(
                    String blankOrEmptyString
            ) {
                // then
                assertThatThrownBy(() ->
                        // given / when
                        given.one()
                                .defaultPrivilege()
                                .withRole(blankOrEmptyString)
                                .withObjectType(SCHEMA)
                                .withPrivileges(USAGE)
                                .apply()
                ).isInstanceOf(KubernetesClientException.class)
                        .hasMessageContaining("The DefaultPrivilege role must not be empty.");
            }

            @ParameterizedTest
            @BlankSource
            @DisplayName("Should fail when the owner is a blank or empty String (CEL rule)")
            void failWhenOwnerIsBlankOrEmptyString(
                    String blankOrEmptyString
            ) {
                // then
                assertThatThrownBy(() ->
                        // given / when
                        given.one()
                                .defaultPrivilege()
                                .withOwner(blankOrEmptyString)
                                .withObjectType(SCHEMA)
                                .withPrivileges(USAGE)
                                .apply()
                ).isInstanceOf(KubernetesClientException.class)
                        .hasMessageContaining("The DefaultPrivilege owner must not be empty.");
            }

            @ParameterizedTest
            @BlankSource
            @DisplayName("Should fail when the schema is a blank or empty String (CEL rule)")
            void failWhenSchemaIsBlankOrEmptyString(
                    String blankOrEmptyString
            ) {
                // then
                assertThatThrownBy(() ->
                        // given / when
                        given.one()
                                .defaultPrivilege()
                                .withSchema(blankOrEmptyString)
                                .withObjectType(SCHEMA)
                                .withPrivileges(USAGE)
                                .apply()
                ).isInstanceOf(KubernetesClientException.class)
                        .hasMessageContaining("The DefaultPrivilege schema must not be empty.");
            }

            @Test
            @DisplayName("Should fail when the privileges are an empty List (CEL rule)")
            void failWhenPrivilegesAreAnEmptyList(
            ) {
                // then
                assertThatThrownBy(() ->
                        // given / when
                        given.one()
                                .defaultPrivilege()
                                .withObjectType(SCHEMA)
                                .withPrivileges(List.of())
                                .apply()
                ).isInstanceOf(KubernetesClientException.class)
                        .hasMessageContaining("The DefaultPrivilege privileges must not be empty.");
            }
        }

        @Nested
        class ImmutableFields {
            @Test
            @DisplayName("Should fail when the database changes (CEL rule)")
            void failWhenDatabaseChanges() {
                // given
                var item = given.one()
                        .defaultPrivilege()
                        .withObjectType(SCHEMA)
                        .withPrivileges(USAGE)
                        .returnFirst();

                // then
                assertThatThrownBy(() -> {
                    // when
                    item.getSpec().setDatabase("new-database");

                    kubernetesClient.resources(DefaultPrivilege.class)
                            .inNamespace(item.getMetadata().getNamespace())
                            .withName(item.getMetadata().getName())
                            .patch(item);
                }).isInstanceOf(KubernetesClientException.class)
                        .hasMessageContaining("The DefaultPrivilege database is immutable.");
            }

            @Test
            @DisplayName("Should fail when the role changes (CEL rule)")
            void failWhenRoleChanges() {
                // given
                var item = given.one()
                        .defaultPrivilege()
                        .withObjectType(SCHEMA)
                        .withPrivileges(USAGE)
                        .returnFirst();

                // then
                assertThatThrownBy(() -> {
                    // when
                    item.getSpec().setRole("new-role");

                    kubernetesClient.resources(DefaultPrivilege.class)
                            .inNamespace(item.getMetadata().getNamespace())
                            .withName(item.getMetadata().getName())
                            .patch(item);
                }).isInstanceOf(KubernetesClientException.class)
                        .hasMessageContaining("The DefaultPrivilege role is immutable.");
            }

            @Test
            @DisplayName("Should fail when the owner changes (CEL rule)")
            void failWhenOwnerChanges() {
                // given
                var item = given.one()
                        .defaultPrivilege()
                        .withObjectType(SCHEMA)
                        .withPrivileges(USAGE)
                        .returnFirst();

                // then
                assertThatThrownBy(() -> {
                    // when
                    item.getSpec().setOwner("new-owner");

                    kubernetesClient.resources(DefaultPrivilege.class)
                            .inNamespace(item.getMetadata().getNamespace())
                            .withName(item.getMetadata().getName())
                            .patch(item);
                }).isInstanceOf(KubernetesClientException.class)
                        .hasMessageContaining("The DefaultPrivilege owner is immutable.");
            }

            @Test
            @DisplayName("Should fail when the schema changes (CEL rule)")
            void failWhenSchemaChanges() {
                // given
                var item = given.one()
                        .defaultPrivilege()
                        .withObjectType(TABLE)
                        .withPrivileges(USAGE)
                        .returnFirst();

                // then
                assertThatThrownBy(() -> {
                    // when
                    item.getSpec().setSchema("new-schema");

                    kubernetesClient.resources(DefaultPrivilege.class)
                            .inNamespace(item.getMetadata().getNamespace())
                            .withName(item.getMetadata().getName())
                            .patch(item);
                }).isInstanceOf(KubernetesClientException.class)
                        .hasMessageContaining("The DefaultPrivilege schema is immutable.");
            }

            @Test
            @DisplayName("Should fail when the objectType changes (CEL rule)")
            void failWhenObjectTypeChanges() {
                // given
                var item = given.one()
                        .defaultPrivilege()
                        .withObjectType(SCHEMA)
                        .withPrivileges(USAGE)
                        .returnFirst();

                // then
                assertThatThrownBy(() -> {
                    // when
                    item.getSpec().setObjectType(SEQUENCE);

                    kubernetesClient.resources(DefaultPrivilege.class)
                            .inNamespace(item.getMetadata().getNamespace())
                            .withName(item.getMetadata().getName())
                            .patch(item);
                }).isInstanceOf(KubernetesClientException.class)
                        .hasMessageContaining("The DefaultPrivilege objectType is immutable.");
            }
        }

        @Nested
        class RelatedFields {
            @Test
            @DisplayName("Should fail when objectType is SCHEMA but schema is set (CEL rule)")
            void failWhenDatabaseHasSchema() {
                // then
                assertThatThrownBy(() ->
                        // given / when
                        given.one()
                                .defaultPrivilege()
                                .withSchema("some-schema")
                                .withObjectType(SCHEMA)
                                .withPrivileges(USAGE)
                                .returnFirst()
                ).isInstanceOf(KubernetesClientException.class)
                        .hasMessageContaining("The DefaultPrivilege schema must be not set if objectType is 'schema'");
            }

            @Test
            @DisplayName("Should reconcile to ERROR when privileges are invalid for objectType")
            void errorWhenInvalidPrivileges() {
                // given
                var now = OffsetDateTime.now(ZoneOffset.UTC);

                // when
                var defaultPrivilege = given.one()
                        .defaultPrivilege()
                        .withObjectType(SCHEMA)
                        // SELECT is not allowed for SCHEMA
                        .withPrivileges(SELECT)
                        .returnFirst();

                var expectedStatus = new CRStatus()
                        .setName("")
                        .setPhase(CRPhase.ERROR)
                        .setMessage("DefaultPrivilege contains invalid privileges for the specified objectType")
                        .setObservedGeneration(1L);

                // then
                assertThatGrantHasStatus(
                        defaultPrivilege,
                        expectedStatus,
                        now
                );
            }
        }
    }

    private void assertThatGrantHasStatus(
            DefaultPrivilege defaultPrivilege,
            CRStatus expectedStatus,
            OffsetDateTime now
    ) {
        assertThat(defaultPrivilege)
                .isNotNull()
                .extracting(DefaultPrivilege::getStatus)
                .satisfies(status -> {
                    assertThat(status.getLastProbeTime()).isAfter(
                            now
                    );
                    assertThat(status.getLastPhaseTransitionTime()).isAfter(
                            now
                    );
                })
                .usingRecursiveComparison()
                .ignoringFields("message", "lastProbeTime", "lastPhaseTransitionTime")
                .isEqualTo(expectedStatus);
    }
}
