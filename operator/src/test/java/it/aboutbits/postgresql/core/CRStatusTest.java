package it.aboutbits.postgresql.core;

import io.fabric8.kubernetes.client.utils.KubernetesSerialization;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@NullMarked
class CRStatusTest {
    @Nested
    class UpdatePhase {
        @Test
        @DisplayName("A new status should start out pending without a transition time")
        void newStatus_isPending() {
            // when
            var status = new CRStatus();

            // then
            assertThat(status.getPhase()).isEqualTo(CRPhase.PENDING);
            assertThat(status.getLastPhaseTransitionTime()).isNull();
        }

        @Test
        @DisplayName("Changing the phase should stamp the transition time")
        void phaseChange_stampsTransitionTime() {
            // given
            var status = new CRStatus();

            // when
            status.updatePhase(CRPhase.READY);

            // then
            assertThat(status.getPhase()).isEqualTo(CRPhase.READY);
            assertThat(status.getLastPhaseTransitionTime()).isNotNull();
        }

        @Test
        @DisplayName("Changing the phase again should replace the previous transition time")
        void secondPhaseChange_replacesTransitionTime() {
            // given
            var json = """
                       {"lastPhaseTransitionTime":"2026-01-01T00:00:00Z","phase":"READY"}
                       """;

            var status = new KubernetesSerialization().unmarshal(
                    json,
                    CRStatus.class
            );

            // when
            status.updatePhase(CRPhase.ERROR);

            // then
            assertThat(status.getPhase()).isEqualTo(CRPhase.ERROR);
            assertThat(status.getLastPhaseTransitionTime())
                    .isNotNull()
                    .isAfter(OffsetDateTime.parse("2026-01-01T00:00:00Z"));
        }

        @Test
        @DisplayName("Setting the same phase again should not touch the transition time")
        void samePhase_keepsTransitionTime() {
            // given
            // A fixed time in the past, so the test fails if the time is overwritten with now.
            var json = """
                       {"lastPhaseTransitionTime":"2026-01-01T00:00:00Z","phase":"READY"}
                       """;

            var status = new KubernetesSerialization().unmarshal(
                    json,
                    CRStatus.class
            );

            // when
            status.updatePhase(CRPhase.READY);

            // then
            assertThat(status.getLastPhaseTransitionTime())
                    .isNotNull()
                    .isAtSameInstantAs(OffsetDateTime.parse("2026-01-01T00:00:00Z"));
        }

        @Test
        @DisplayName("Updating the phase should be chainable with the remaining status setters")
        void updatePhase_isChainable() {
            // when
            var status = new CRStatus()
                    .updatePhase(CRPhase.ERROR)
                    .setMessage("Something went wrong");

            // then
            assertThat(status.getPhase()).isEqualTo(CRPhase.ERROR);
            assertThat(status.getMessage()).isEqualTo("Something went wrong");
        }
    }

    @Nested
    class Deserialize {
        @Test
        @DisplayName("Reading a status from the cluster should keep its transition time")
        void statusFromCluster_keepsTransitionTime() {
            // given
            // The API server sends the status keys in alphabetical order.
            var json = """
                       {"lastPhaseTransitionTime":"2026-01-01T00:00:00Z","phase":"READY"}
                       """;

            // when
            var status = new KubernetesSerialization().unmarshal(
                    json,
                    CRStatus.class
            );

            // then
            assertThat(status.getPhase()).isEqualTo(CRPhase.READY);
            assertThat(status.getLastPhaseTransitionTime())
                    .isNotNull()
                    .isAtSameInstantAs(OffsetDateTime.parse("2026-01-01T00:00:00Z"));
        }
    }
}
