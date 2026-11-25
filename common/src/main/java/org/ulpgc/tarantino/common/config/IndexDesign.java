package org.ulpgc.tarantino.common.config;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/** The two index designs of SPEC §11, selected with {@code TARANTINO_INDEX_DESIGN}. */
public enum IndexDesign {
    LOCAL,
    GRID;

    /** The value of {@code TARANTINO_INDEX_DESIGN}, its name in lower case. */
    public String variableValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    static Optional<IndexDesign> of(String value) {
        return Arrays.stream(values()).filter(design -> design.variableValue().equals(value)).findFirst();
    }
}
