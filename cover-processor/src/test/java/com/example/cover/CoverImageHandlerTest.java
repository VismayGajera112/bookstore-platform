package com.example.cover;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CoverImageHandlerTest {

    @Test
    void parseBookId_fromCanonicalKey() {
        assertEquals(42L, CoverImageHandler.parseBookId("covers/42/cover.jpg"));
        assertEquals(1L, CoverImageHandler.parseBookId("covers/1/nested/photo.png"));
    }

    @Test
    void parseBookId_rejectsUnexpectedKeys() {
        assertNull(CoverImageHandler.parseBookId("other/42/cover.jpg"));
        assertNull(CoverImageHandler.parseBookId("covers/not-a-number/cover.jpg"));
        assertNull(CoverImageHandler.parseBookId("covers/"));
    }
}
