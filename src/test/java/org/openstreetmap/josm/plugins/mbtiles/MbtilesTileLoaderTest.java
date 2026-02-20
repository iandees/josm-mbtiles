package org.openstreetmap.josm.plugins.mbtiles;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.sqlite.SQLiteConfig;

class MbtilesTileLoaderTest {

    /**
     * Tests the Y-coordinate inversion formula used by MbtilesTileLoader:
     * invY = (int) Math.pow(2, zoom) - 1 - y
     *
     * This converts between JOSM's top-origin Y and MBTiles/TMS bottom-origin Y.
     */
    @ParameterizedTest
    @CsvSource({
            "0, 0, 0",       // zoom=0: single tile, y=0 -> invY=0
            "1, 0, 1",       // zoom=1: 2 tiles, y=0 (top) -> invY=1 (bottom)
            "1, 1, 0",       // zoom=1: y=1 (bottom) -> invY=0 (top)
            "2, 0, 3",       // zoom=2: 4 tiles, y=0 -> invY=3
            "2, 3, 0",       // zoom=2: y=3 -> invY=0
            "2, 1, 2",       // zoom=2: y=1 -> invY=2
            "10, 500, 523",  // zoom=10: 1024 tiles, y=500 -> invY=523
            "18, 0, 262143", // zoom=18: 2^18-1 = 262143
    })
    void yCoordinateInversion(int zoom, int y, int expectedInvY) {
        int invY = (int) Math.pow(2, zoom) - 1 - y;
        assertEquals(expectedInvY, invY,
                String.format("At zoom=%d, y=%d should invert to %d", zoom, y, expectedInvY));
    }

    @Test
    void yCoordinateInversionIsSymmetric() {
        // Applying the inversion twice should return the original value
        for (int zoom = 0; zoom <= 18; zoom++) {
            int maxTile = (int) Math.pow(2, zoom) - 1;
            for (int y = 0; y <= Math.min(maxTile, 10); y++) {
                int invY = (int) Math.pow(2, zoom) - 1 - y;
                int backToOriginal = (int) Math.pow(2, zoom) - 1 - invY;
                assertEquals(y, backToOriginal,
                        String.format("Double inversion at zoom=%d, y=%d should return original", zoom, y));
            }
        }
    }

    @Test
    void tileRetrievalFromDatabase() throws Exception {
        // Create a database with a known tile at a specific TMS coordinate
        File dbFile = MbtilesTestUtils.createTestMbtilesDb("test", null, 0, 2);

        // Insert a tile with known data at zoom=2, col=1, row=3 (TMS coords)
        byte[] expectedData = MbtilesTestUtils.createMinimalPng();
        MbtilesTestUtils.insertTile(dbFile, 2, 1, 3, expectedData);

        // Now query the same way MbtilesTileLoader does:
        // JOSM y=0 at zoom=2 -> invY = 4-1-0 = 3 (TMS row)
        Class.forName("org.sqlite.JDBC");
        SQLiteConfig config = new SQLiteConfig();
        config.setReadOnly(true);
        Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath(), config.toProperties());

        int josmY = 0;
        int zoom = 2;
        int tileX = 1;
        int invY = (int) Math.pow(2, zoom) - 1 - josmY;

        Statement stmt = conn.createStatement();
        String sql = "SELECT tile_data FROM tiles WHERE zoom_level=" + zoom +
                " AND tile_column=" + tileX + " AND tile_row=" + invY + " LIMIT 1";
        ResultSet rs = stmt.executeQuery(sql);

        assertTrue(rs.next(), "Tile should be found at the inverted Y coordinate");
        byte[] actualData = rs.getBytes(1);
        assertArrayEquals(expectedData, actualData, "Tile data should match what was inserted");

        rs.close();
        stmt.close();
        conn.close();
    }

    @Test
    void missingTileReturnsNoResults() throws Exception {
        File dbFile = MbtilesTestUtils.createTestMbtilesDb("test", null, 0, 2);

        Class.forName("org.sqlite.JDBC");
        SQLiteConfig config = new SQLiteConfig();
        config.setReadOnly(true);
        Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath(), config.toProperties());

        Statement stmt = conn.createStatement();
        // Query for a tile that doesn't exist
        String sql = "SELECT tile_data FROM tiles WHERE zoom_level=15 AND tile_column=999 AND tile_row=999 LIMIT 1";
        ResultSet rs = stmt.executeQuery(sql);

        assertFalse(rs.next(), "No tile should be found for coordinates that don't exist");

        rs.close();
        stmt.close();
        conn.close();
    }
}
