package org.ulpgc.tarantino.common.datalake;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class DocumentDigestTest {

    @Test
    void hashesTheHeaderFollowedByTheBodyAsShasumDoes() {
        assertEquals("15db630ac40c7ccb2da7a53d773935c4ccef4499ced71700d123af45fe0b794e",
                DocumentDigest.of("Title: Pride and Prejudice\n", "It is a truth universally acknowledged"));
    }

    @Test
    void hashesTheUtf8BytesOfTheText() {
        assertEquals("63373eb3ae620197f4c45a85d007ac1062be905362dd56496f31df13751a2f29", DocumentDigest.of("Título: ", "Ñandú"));
    }

    @Test
    void changesWithAnyByteOfEitherPart() {
        assertNotEquals(DocumentDigest.of("Title: A\n", "body"), DocumentDigest.of("Title: A\n", "body."));
    }
}
