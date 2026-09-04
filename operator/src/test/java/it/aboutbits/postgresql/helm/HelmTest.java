package it.aboutbits.postgresql.helm;

import io.fabric8.kubernetes.api.model.ConfigBuilder;
import io.fabric8.kubernetes.api.model.LocalObjectReference;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.utils.Serialization;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.common.process.ProcessBuilder;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/// See https://github.com/quarkiverse/quarkus-operator-sdk/blob/7.7.4/samples/exposedapp/src/test/java/io/halkyon/HelmDeploymentE2EIT.java
@Slf4j
@QuarkusTest
@NullMarked
class HelmTest {
    private static final String ENV_VAR_KUBECONFIG = "KUBECONFIG";

    /// The Pod and Container list fields that the chart exposes as free-form Helm values.
    private static final List<String> LIST_VALUES = List.of(
            "imagePullSecrets",
            "volumes",
            "volumeMounts"
    );

    private static final String CRD_GROUP = "postgresql.aboutbits.it";
    private static final List<String> CRD_NAMES = List.of(
            "clusterconnection",
            "database",
            "schema",
            "role",
            "grant",
            "defaultprivilege"
    );

    private final String chartName;
    /// Dekorate uses this value for the Deployment name and for the container name.
    private final String kubernetesName;
    private final String rootValuesAlias;
    private final KubernetesClient kubernetesClient;

    HelmTest(
            KubernetesClient kubernetesClient,
            @ConfigProperty(name = "quarkus.helm.name") String chartName,
            @ConfigProperty(name = "quarkus.kubernetes.name") String kubernetesName,
            @ConfigProperty(name = "quarkus.helm.values-root-alias", defaultValue = "app") String rootValuesAlias
    ) {
        this.kubernetesClient = kubernetesClient;
        this.chartName = chartName;
        this.kubernetesName = kubernetesName;
        this.rootValuesAlias = rootValuesAlias;
    }

    @SuppressWarnings("checkstyle:MethodLength")
    @Test
    @DisplayName("When the Helm chart is installed, the operator deployment should be created")
    void helmInstall_createsDeployment() throws IOException {
        // given
        var chartPath = chartPath();

        assertThat(chartPath)
                .withFailMessage("Helm chart not found at %s. Ensure that the chart is generated before running this test.", chartPath)
                .exists();

        // 1. Verify files exist and contain expected data
        // ./Chart.yaml
        @SuppressWarnings("unchecked")
        Map<String, Object> chartMetadata = Serialization.yamlMapper()
                .readValue(
                        chartPath.resolve("Chart.yaml").toFile(),
                        Map.class
                );

        assertThat(chartMetadata.get("name")).isEqualTo(chartName);

        // ./values.yaml
        @SuppressWarnings("unchecked")
        Map<String, Object> values = Serialization.yamlMapper()
                .readValue(
                        chartPath.resolve("values.yaml").toFile(),
                        Map.class
                );

        assertThat(values).containsKey(rootValuesAlias);

        @SuppressWarnings("unchecked")
        var appValues = (Map<String, Object>) values.get(rootValuesAlias);

        Objects.requireNonNull(appValues, "appValues should not be null");
        assertThat(appValues.get("image")).isNotNull();

        // The list values must default to a real empty list, not to `- {}`.
        // `operator/src/main/helm/values.yaml` provides these defaults.
        for (var listValue : LIST_VALUES) {
            assertThat(appValues.get(listValue))
                    .withFailMessage("app.%s should default to an empty list, but was %s", listValue, appValues.get(listValue))
                    .isEqualTo(List.of());
        }

        assertThat(chartPath.resolve("LICENSE")).exists();
        assertThat(chartPath.resolve("README.md")).exists();
        assertThat(chartPath.resolve("values.schema.json")).exists();

        // ./values.schema.json
        // The type must be declared for every list value, otherwise the generated schema
        // falls back to `string` and `helm install` rejects a list.
        var valuesSchema = Serialization.jsonMapper()
                .readTree(chartPath.resolve("values.schema.json").toFile());

        for (var listValue : LIST_VALUES) {
            var schemaProperty = valuesSchema.at("/properties/%s/properties/%s".formatted(
                    rootValuesAlias,
                    listValue
            ));

            assertThat(schemaProperty.path("type").asText())
                    .withFailMessage("app.%s should be typed as an array in values.schema.json", listValue)
                    .isEqualTo("array");

            // A value that `src/main/helm/values.yaml` provides loses the description of its
            // `quarkus.helm.values` entry, so the description has to come from the schema.
            assertThat(schemaProperty.path("description").asText())
                    .withFailMessage("app.%s should have a description in values.schema.json", listValue)
                    .isNotBlank();
        }

        // ./crds/
        for (var crdName : CRD_NAMES) {
            assertThat(chartPath.resolve("crds/%ss.%s-v1.yml".formatted(
                    crdName,
                    CRD_GROUP
            ))).exists();
        }

        // ./templates/
        assertThat(chartPath.resolve("templates/clusterrole.yaml")).exists();
        assertThat(chartPath.resolve("templates/clusterrolebinding.yaml")).exists();
        assertThat(chartPath.resolve("templates/deployment.yaml")).exists();
        assertThat(chartPath.resolve("templates/rolebinding.yaml")).exists();
        assertThat(chartPath.resolve("templates/service.yaml")).exists();
        assertThat(chartPath.resolve("templates/serviceaccount.yaml")).exists();
        assertThat(chartPath.resolve("templates/validating-clusterrolebinding.yaml")).exists();

        // The indent of each expression has to match the depth of its field in the Deployment.
        // A wrong `nindent` produces invalid YAML as soon as a user sets the value.
        assertThat(chartPath.resolve("templates/deployment.yaml"))
                .content()
                .contains("imagePullSecrets: {{- toYaml (.Values.app.imagePullSecrets | default list) | nindent 8 }}")
                .contains("volumes: {{- toYaml (.Values.app.volumes | default list) | nindent 8 }}")
                .contains("volumeMounts: {{- toYaml (.Values.app.volumeMounts | default list) | nindent 12 }}");

        for (var crdName : CRD_NAMES) {
            assertThat(chartPath.resolve("templates/%sreconciler-crd-role-binding.yaml".formatted(
                    crdName
            ))).exists();
        }

        // 2. Prepare a temporary KubeConfig for the 'helm' CLI
        // This ensures 'helm' uses the same Kubernetes cluster as the test environment (e.g., provided by DevServices).
        var kubeConfigPath = createTempKubeConfig();

        try {
            // 3. Install the Helm chart using 'helm install'
            var releaseName = "helm-install-test-" + System.nanoTime();

            var holder = new Object() {
                int exitCode;
            };
            var installOutput = new StringBuilder();

            ProcessBuilder.newBuilder(
                            "helm",
                            "install", releaseName, chartPath.toAbsolutePath().toString(), "--set", rootValuesAlias + ".image=postgresql-operator:test"
                    ).environment(Map.of(
                            ENV_VAR_KUBECONFIG,
                            kubeConfigPath.toAbsolutePath().toString()
                    ))
                    .exitCodeChecker(ec -> {
                        holder.exitCode = ec;
                        return true;
                    })
                    .error().redirect()
                    .output()
                    .consumeLinesWith(65536, line -> installOutput.append(line).append(System.lineSeparator()))
                    .run();

            int installExitCode = holder.exitCode;
            assertThat(installExitCode)
                    .withFailMessage("Helm install failed with output:\n" + installOutput)
                    .isZero();

            try {
                // 4. Verify that the deployment is created in Kubernetes
                await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                    var deployment = kubernetesClient.apps().deployments().withName(chartName).get();

                    assertThat(deployment).isNotNull();

                    // Helm sets labels based on the release name
                    assertThat(deployment.getMetadata())
                            .isNotNull()
                            .satisfies(metadata -> assertThat(metadata.getLabels())
                                    .containsAllEntriesOf(Map.of(
                                            "app.kubernetes.io/name", releaseName,
                                            "app.kubernetes.io/managed-by", "Helm"
                                    ))
                            );

                    assertThat(deployment.getSpec())
                            .isNotNull()
                            .satisfies(spec -> {
                                var podSpec = spec.getTemplate().getSpec();

                                assertThat(podSpec.getImagePullSecrets()).isEmpty();
                                assertThat(podSpec.getVolumes()).isEmpty();

                                // The baseline `kubernetes.yml` names the container, so Dekorate must
                                // not add a second one.
                                assertThat(podSpec.getContainers())
                                        .singleElement()
                                        .satisfies(container -> {
                                            assertThat(container.getName()).isEqualTo(kubernetesName);
                                            assertThat(container.getVolumeMounts()).isEmpty();
                                        });
                            });

                    var selector = deployment.getSpec().getSelector();

                    var pods = kubernetesClient.pods()
                            .withLabelSelector(selector)
                            .list()
                            .getItems();

                    assertThat(pods).isNotEmpty();
                });
            } finally {
                // 5. Cleanup the created resources using 'helm uninstall'
                ProcessBuilder.newBuilder(
                                "helm",
                                "uninstall", releaseName
                        ).environment(Map.of(
                                ENV_VAR_KUBECONFIG,
                                kubeConfigPath.toAbsolutePath().toString()
                        ))
                        .error().consumeLinesWith(
                                8192,
                                log::error
                        )
                        .run();
            }
        } finally {
            Files.deleteIfExists(kubeConfigPath);
        }
    }

    @Test
    @DisplayName("When the chart is rendered with volumes, the deployment should mount them")
    void helmTemplate_rendersVolumes() throws IOException {
        // given
        var chartPath = chartPath();

        assertThat(chartPath)
                .withFailMessage("Helm chart not found at %s. Ensure that the chart is generated before running this test.", chartPath)
                .exists();

        var valuesPath = createTempValuesWithVolumes();

        try {
            // `helm template` needs no cluster, and it validates the values against values.schema.json.
            var holder = new Object() {
                int exitCode;
            };
            var renderedOutput = new StringBuilder();

            // when
            ProcessBuilder.newBuilder(
                            "helm",
                            "template", "volumes-render-test", chartPath.toAbsolutePath().toString(),
                            "--values", valuesPath.toAbsolutePath().toString()
                    )
                    .exitCodeChecker(ec -> {
                        holder.exitCode = ec;
                        return true;
                    })
                    .error().consumeLinesWith(8192, log::error)
                    .output()
                    .consumeLinesWith(65536, line -> renderedOutput.append(line).append(System.lineSeparator()))
                    .run();

            // then
            assertThat(holder.exitCode)
                    .withFailMessage("Helm template failed, see the logged error output")
                    .isZero();

            var deployments = kubernetesClient.load(new ByteArrayInputStream(
                            renderedOutput.toString().getBytes(StandardCharsets.UTF_8)
                    ))
                    .items()
                    .stream()
                    .filter(Deployment.class::isInstance)
                    .map(Deployment.class::cast)
                    .toList();

            assertThat(deployments)
                    .withFailMessage("The rendered chart must contain exactly one Deployment:%n%s", renderedOutput)
                    .hasSize(1);

            var deployment = deployments.getFirst();

            // The baseline `kubernetes.yml` must name the Deployment `quarkus.kubernetes.name`.
            // Dekorate keeps a different name as a second Deployment.
            assertThat(deployment.getMetadata().getName()).isEqualTo(kubernetesName);

            var podSpec = deployment.getSpec().getTemplate().getSpec();

            assertThat(podSpec.getVolumes())
                    .extracting(Volume::getName)
                    .containsExactly("db-credentials", "aws-secrets");

            // A Secrets Store CSI volume is the case that `adminSecretFileRef` was added for.
            assertThat(podSpec.getVolumes())
                    .filteredOn(volume -> "aws-secrets".equals(volume.getName()))
                    .singleElement()
                    .satisfies(volume -> assertThat(volume.getCsi())
                            .isNotNull()
                            .satisfies(csi -> {
                                assertThat(csi.getDriver()).isEqualTo("secrets-store.csi.k8s.io");
                                assertThat(csi.getVolumeAttributes())
                                        .containsEntry("secretProviderClass", "db-credentials");
                            })
                    );

            assertThat(podSpec.getImagePullSecrets())
                    .extracting(LocalObjectReference::getName)
                    .containsExactly("my-registry-secret");

            assertThat(podSpec.getContainers())
                    .singleElement()
                    .satisfies(container -> {
                        assertThat(container.getName()).isEqualTo(kubernetesName);
                        assertThat(container.getVolumeMounts())
                                .extracting(VolumeMount::getMountPath)
                                .containsExactly("/mnt/secrets", "/mnt/aws");
                    });
        } finally {
            Files.deleteIfExists(valuesPath);
        }
    }

    /// The chart is generated by the quarkus-helm extension in the build directory.
    /// For Gradle, it's build/helm/kubernetes/postgresql-operator
    private Path chartPath() {
        return Paths.get("build", "helm", "kubernetes", chartName);
    }

    private static Path createTempValuesWithVolumes() throws IOException {
        var values =
                """
                app:
                  image: postgresql-operator:test
                  imagePullSecrets:
                    - name: my-registry-secret
                  volumes:
                    - name: db-credentials
                      secret:
                        secretName: db-credentials-secret
                    - name: aws-secrets
                      csi:
                        driver: secrets-store.csi.k8s.io
                        readOnly: true
                        volumeAttributes:
                          secretProviderClass: db-credentials
                  volumeMounts:
                    - name: db-credentials
                      mountPath: /mnt/secrets
                      readOnly: true
                    - name: aws-secrets
                      mountPath: /mnt/aws
                      readOnly: true
                """;

        var path = Files.createTempFile("values-volumes-helm-test-", ".yaml");

        Files.writeString(path, values);

        return path;
    }

    private Path createTempKubeConfig() throws IOException {
        var clientConfig = kubernetesClient.getConfiguration();

        var kubeConfig = new ConfigBuilder()
                .addNewCluster()
                .withName("dev-cluster")
                .withNewCluster()
                .withServer(clientConfig.getMasterUrl())
                .withCertificateAuthorityData(clientConfig.getCaCertData())
                .endCluster()
                .endCluster()
                .addNewUser()
                .withName("dev-user")
                .withNewUser()
                .withClientCertificateData(clientConfig.getClientCertData())
                .withClientKeyData(clientConfig.getClientKeyData())
                .endUser()
                .endUser()
                .addNewContext()
                .withName("dev-context")
                .withNewContext()
                .withCluster("dev-cluster")
                .withUser("dev-user")
                .withNamespace(clientConfig.getNamespace())
                .endContext()
                .endContext()
                .withCurrentContext("dev-context")
                .build();

        var path = Files.createTempFile("kubeconfig-helm-test-", ".yaml");

        Files.writeString(path, Serialization.asYaml(kubeConfig));

        return path;
    }
}
