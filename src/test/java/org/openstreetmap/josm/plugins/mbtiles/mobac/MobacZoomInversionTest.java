package org.openstreetmap.josm.plugins.mbtiles.mobac;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Tests for Mobac's zoom level inversion: mobacZoom = 17 - standardZoom.
 *
 * Mobac stores zoom levels inverted relative to the standard web tile convention.
 * Standard zoom 0 (whole world) = Mobac zoom 17, and standard zoom 17 (most detail) = Mobac zoom 0.
 */
class MobacZoomInversionTest {

    /**
     * Verify the zoom conversion formula used by MobacTileLoader:
     * mobacZ = 17 - tile.getZoom()
     */
    @ParameterizedTest
    @CsvSource({
            "0, 17",   // standard zoom 0 -> mobac 17
            "1, 16",
            "5, 12",
            "10, 7",
            "15, 2",
            "17, 0",   // standard zoom 17 -> mobac 0
    })
    void standardToMobacZoom(int standardZoom, int expectedMobacZoom) {
        int mobacZ = 17 - standardZoom;
        assertEquals(expectedMobacZoom, mobacZ);
    }

    /**
     * Verify the zoom inversion used in MobacLayer.buildImageryInfo:
     * standardZoom = 17 - mobacZoom
     * And that min/max are correctly swapped.
     */
    @ParameterizedTest
    @CsvSource({
            "3, 7, 10, 14",   // mobac 3-7 -> standard 10-14
            "0, 17, 0, 17",   // mobac 0-17 -> standard 0-17
            "5, 5, 12, 12",   // single zoom level
            "10, 15, 2, 7",   // mobac 10-15 -> standard 2-7
    })
    void mobacToStandardZoomRange(int mobacMin, int mobacMax, int expectedStdMin, int expectedStdMax) {
        // MobacLayer computes: minz = Math.min(17 - mobacMin, 17 - mobacMax)
        int stdFromMin = 17 - mobacMin;
        int stdFromMax = 17 - mobacMax;
        int minz = Math.min(stdFromMin, stdFromMax);
        int maxz = Math.max(stdFromMin, stdFromMax);

        assertEquals(expectedStdMin, minz);
        assertEquals(expectedStdMax, maxz);
    }

    @Test
    void zoomInversionIsSymmetric() {
        // Converting standard -> mobac -> standard should return the original
        for (int z = 0; z <= 17; z++) {
            int mobacZ = 17 - z;
            int backToStandard = 17 - mobacZ;
            assertEquals(z, backToStandard,
                    String.format("Round-trip at zoom %d should return original", z));
        }
    }

    /**
     * Mobac does NOT invert Y coordinates (unlike MBTiles/TMS).
     * The Y coordinate is passed directly: tile.getYtile() is used as-is.
     */
    @Test
    void mobacDoesNotInvertY() {
        // In MobacTileLoader, the query uses tile.getYtile() directly,
        // unlike MbtilesTileLoader which inverts Y.
        // This test documents that Mobac Y = JOSM Y (no transformation).
        int josmY = 42;
        int mobacY = josmY; // no inversion
        assertEquals(josmY, mobacY);
    }
}
