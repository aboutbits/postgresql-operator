package it.aboutbits.postgresql.core;

import org.jspecify.annotations.NullMarked;

@NullMarked
public enum CRPhase {
    PENDING,
    READY,
    ERROR,
    DELETING
}
