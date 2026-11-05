package it.aboutbits.gradle.format;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JavaLayoutTest {
    private static void assertFormat(String input, String expected) {
        assertEquals(expected, JavaLayout.format(input));
        // A second run must not change the result
        assertEquals(expected, JavaLayout.format(expected));
    }

    private static void assertUnchanged(String input) {
        assertEquals(input, JavaLayout.format(input));
    }

    @Nested
    class Parameters {
        @Test
        void closingParenthesisOfWrappedMethodGoesOnItsOwnLine() {
            assertFormat(
                    """
                    class A {
                        public String find(
                                String namespace, String name, int limit) {
                            return name;
                        }
                    }
                    """,
                    """
                    class A {
                        public String find(
                                String namespace, String name, int limit
                        ) {
                            return name;
                        }
                    }
                    """
            );
        }

        @Test
        void closingParenthesisOfChoppedConstructorGoesOnItsOwnLine() {
            assertFormat(
                    """
                    class A {
                        A(
                                String namespace,
                                String name) {}
                    }
                    """,
                    """
                    class A {
                        A(
                                String namespace,
                                String name
                        ) {}
                    }
                    """
            );
        }

        @Test
        void throwsClauseFollowsTheClosingParenthesis() {
            assertFormat(
                    """
                    class A {
                        void run(
                                String namespace, String name)
                                throws Exception {}
                    }
                    """,
                    """
                    class A {
                        void run(
                                String namespace, String name
                        ) throws Exception {}
                    }
                    """
            );
        }

        @Test
        void abstractMethodKeepsTheSemicolon() {
            assertFormat(
                    """
                    interface A {
                        void run(
                                String namespace, String name);
                    }
                    """,
                    """
                    interface A {
                        void run(
                                String namespace, String name
                        );
                    }
                    """
            );
        }

        @Test
        void methodOnOneLineStaysUnchanged() {
            assertUnchanged(
                    """
                    class A {
                        void run(String namespace, String name) {}
                    }
                    """
            );
        }

        @Test
        void closingParenthesisOfWrappedRecordHeaderGoesOnItsOwnLine() {
            assertFormat(
                    """
                    public record Credentials(
                            String username, String password) {}
                    """,
                    """
                    public record Credentials(
                            String username, String password
                    ) {}
                    """
            );
        }
    }

    @Nested
    class Annotations {
        @Test
        void closingParenthesisOfMultiLineAnnotationGoesOnItsOwnLine() {
            assertFormat(
                    """
                    class A {
                        @ValidationRule(
                                value = "self == oldSelf",
                                message = "The name is immutable.")
                        private String name;
                    }
                    """,
                    """
                    class A {
                        @ValidationRule(
                                value = "self == oldSelf",
                                message = "The name is immutable."
                        )
                        private String name;
                    }
                    """
            );
        }

        @Test
        void nestedAnnotationInArrayGetsItsOwnClosingLine() {
            assertFormat(
                    """
                    @Rules({
                        @Rule(
                                groups = {""},
                                verbs = {"get", "list"})
                    })
                    class A {}
                    """,
                    """
                    @Rules({
                        @Rule(
                                groups = {""},
                                verbs = {"get", "list"}
                        )
                    })
                    class A {}
                    """
            );
        }

        @Test
        void annotationOnOneLineStaysUnchanged() {
            assertUnchanged(
                    """
                    @SchemaCustomizer(value = HostCustomizer.class, input = "host")
                    class A {}
                    """
            );
        }
    }

    @Nested
    class FieldAnnotations {
        @Test
        void declarationAnnotationGoesOnItsOwnLine() {
            assertFormat(
                    """
                    class A {
                        @io.fabric8.generator.annotation.Nullable private @Nullable ResourceRef adminSecretRef;
                    }
                    """,
                    """
                    class A {
                        @io.fabric8.generator.annotation.Nullable
                        private @Nullable ResourceRef adminSecretRef;
                    }
                    """
            );
        }

        @Test
        void everyDeclarationAnnotationGetsItsOwnLine() {
            assertFormat(
                    """
                    class A {
                        @Required @Min(1) private int port = -1;
                    }
                    """,
                    """
                    class A {
                        @Required
                        @Min(1)
                        private int port = -1;
                    }
                    """
            );
        }

        @Test
        void typeUseAnnotationAfterModifiersStaysInPlace() {
            assertUnchanged(
                    """
                    class A {
                        private @Nullable String name = null;
                    }
                    """
            );
        }

        @Test
        void annotationWithoutModifiersStaysInPlace() {
            assertUnchanged(
                    """
                    class A {
                        @Nullable String name = null;
                    }
                    """
            );
        }

        @Test
        void localVariableStaysUnchanged() {
            assertUnchanged(
                    """
                    class A {
                        void run() {
                            @SuppressWarnings("unused") final var name = "a";
                        }
                    }
                    """
            );
        }
    }

    @Nested
    class Ternary {
        @Test
        void conditionMovesToTheEqualsLine() {
            assertFormat(
                    """
                    class A {
                        void run() {
                            var expectedFingerprint =
                                    password != null
                                            ? passwordFingerprintService.fingerprint(password, passwordEncryption)
                                            : null;
                        }
                    }
                    """,
                    """
                    class A {
                        void run() {
                            var expectedFingerprint = password != null
                                    ? passwordFingerprintService.fingerprint(password, passwordEncryption)
                                    : null;
                        }
                    }
                    """
            );
        }

        @Test
        void ternaryOnOneContinuationLineIsSplit() {
            assertFormat(
                    """
                    class A {
                        void run() {
                            var expectedFingerprint =
                                    password != null ? fingerprintService.fingerprint(password) : null;
                        }
                    }
                    """,
                    """
                    class A {
                        void run() {
                            var expectedFingerprint = password != null
                                    ? fingerprintService.fingerprint(password)
                                    : null;
                        }
                    }
                    """
            );
        }

        @Test
        void wrappedBranchMovesWithIt() {
            assertFormat(
                    """
                    class A {
                        void run() {
                            secret =
                                    password != null
                                            ? service.getSecretRefCredentials(
                                                            kubernetesClient, passwordSecretRef, namespace)
                                                    .password()
                                            : null;
                        }
                    }
                    """,
                    """
                    class A {
                        void run() {
                            secret = password != null
                                    ? service.getSecretRefCredentials(
                                                    kubernetesClient, passwordSecretRef, namespace)
                                            .password()
                                    : null;
                        }
                    }
                    """
            );
        }

        @Test
        void ternaryOnTheEqualsLineStaysUnchanged() {
            assertUnchanged(
                    """
                    class A {
                        void run() {
                            var name = value != null ? value : "default";
                        }
                    }
                    """
            );
        }

        @Test
        void tooLongConditionStaysUnchanged() {
            assertUnchanged(
                    """
                    class A {
                        void run() {
                            var expectedFingerprintOfTheCurrentPasswordOfTheRole =
                                    passwordOfTheRoleThatTheOperatorManagesInTheCluster != null
                                            ? fingerprint(passwordOfTheRoleThatTheOperatorManagesInTheCluster)
                                            : null;
                        }
                    }
                    """
            );
        }

        @Test
        void ternaryWithCommentStaysUnchanged() {
            assertUnchanged(
                    """
                    class A {
                        void run() {
                            var name =
                                    value != null
                                            // the value from the spec
                                            ? value
                                            : "default";
                        }
                    }
                    """
            );
        }
    }
}
