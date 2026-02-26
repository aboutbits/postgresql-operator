package it.aboutbits.postgresql.core;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Status Object for the Custom Resources.
 * <p>
 * This object captures the current state of a Custom Resource as observed by the reconciler.
 */
@Getter
@Setter
@Accessors(chain = true)
@NullMarked
public class CRStatus {
    /**
     * The Custom Resource name (may differ from metadata.name).
     */
    private @Nullable String name = null;

    /**
     * Current lifecycle phase of the Bucket.
     */
    @Setter(AccessLevel.NONE)
    private CRPhase phase = CRPhase.PENDING;

    /**
     * Human-readable message providing details about the current state.
     */
    private @Nullable String message = null;

    /**
     * Last time the condition was probed/updated.
     */
    private @Nullable OffsetDateTime lastProbeTime = null;

    /**
     * Last time the condition transitioned from one status to another.
     */
    @Setter(AccessLevel.NONE)
    private @Nullable OffsetDateTime lastPhaseTransitionTime = null;

    /**
     * Observed resource generation that the controller acted upon.
     */
    private long observedGeneration = 0;

    /**
     * Update the current phase. When the phase changes, the {@link #lastPhaseTransitionTime}
     * is updated to the current UTC time and the message is set to {@code null}.
     *
     * @param newPhase the new phase
     * @return this status instance
     */
    public CRStatus setPhase(CRPhase newPhase) {
        if (this.phase == newPhase) {
            return this;
        }

        this.phase = newPhase;
        this.lastPhaseTransitionTime = OffsetDateTime.now(ZoneOffset.UTC);

        return this;
    }
}
