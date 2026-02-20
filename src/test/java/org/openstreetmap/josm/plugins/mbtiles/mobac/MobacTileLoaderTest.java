package org.openstreetmap.josm.plugins.mbtiles.mobac;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.plugins.mbtiles.MbtilesTestUtils;
import org.sqlite.SQLiteConfig;

/**
 * Tests for the Mobac tile loading SQL queries.
 *
 * MobacTileLoader queries: SELECT image FROM tiles WHERE z={mobacZ} AND x={x} AND y={y}
 * where mobacZ = 17 - standardZoom, and y is NOT inverted.
 */
class MobacTileLoaderTest {

    @Test
    void tileRetrievalWithZoomInversion() throws Exception {
        // Create a Mobac database with Mobac zoom range 5-10 (standard 7-12)
        File dbFile = MobacTestUtils.createMobacDb(5, 10);

        // Insert a tile at Mobac coords: z=7, x=3, y=5
        byte[] expectedData = MbtilesTestUtils.createMinimalPng();
        MobacTestUtils.insertTile(dbFile, 7, 3, 5, expectedData);

        Connection conn = openReadOnly(dbFile);

        // Simulate MobacTileLoader: standard zoom 10 -> mobacZ = 17 - 10 = 7
        int standardZoom = 10;
        int mobacZ = 17 - standardZoom;
        int x = 3;
        int y = 5; // no Y inversion in Mobac

        Statement stmt = conn.createStatement();
        String sql = "SELECT image FROM tiles WHERE z=" + mobacZ + " AND x=" + x + " AND y=" + y + " LIMIT 1";
        ResultSet rs = stmt.executeQuery(sql);

        assertTrue(rs.next(), "Tile should be found using inverted zoom");
        assertArrayEquals(expectedData, rs.getBytes(1), "Tile data should match");

        rs.close();
        stmt.close();
        conn.close();
    }

    @Test
    void missingTileReturnsNoResults() throws Exception {
        File dbFile = MobacTestUtils.createMobacDb(5, 10);

        Connection conn = openReadOnly(dbFile);
        Statement stmt = conn.createStatement();

        // Query for a tile that doesn't exist
        String sql = "SELECT image FROM tiles WHERE z=99 AND x=999 AND y=999 LIMIT 1";
        ResultSet rs = stmt.executeQuery(sql);

        assertFalse(rs.next(), "No tile should be found for non-existent coordinates");

        rs.close();
        stmt.close();
        conn.close();
    }

    @Test
    void multipleTilesAtDifferentZoomLevels() throws Exception {
        File dbFile = MobacTestUtils.createMobacDb(3, 7);

        // Insert tiles at different Mobac zoom levels with unique data
        for (int mobacZ = 3; mobacZ <= 7; mobacZ++) {
            byte[] data = new byte[]{(byte) mobacZ, 1, 2};
            MobacTestUtils.insertTile(dbFile, mobacZ, 1, 2, data);
        }

        Connection conn = openReadOnly(dbFile);
        Statement stmt = conn.createStatement();

        // Query each tile using the standard zoom -> mobac zoom conversion
        for (int standardZoom = 10; standardZoom <= 14; standardZoom++) {
            int mobacZ = 17 - standardZoom;
            String sql = "SELECT image FROM tiles WHERE z=" + mobacZ + " AND x=1 AND y=2 LIMIT 1";
            ResultSet rs = stmt.executeQuery(sql);

            assertTrue(rs.next(), "Tile at standard zoom " + standardZoom + " (mobac " + mobacZ + ") should exist");
            byte[] data = rs.getBytes(1);
            assertEquals((byte) mobacZ, data[0], "Tile data should match the mobac zoom level");
            rs.close();
        }

        stmt.close();
        conn.close();
    }

    private Connection openReadOnly(File dbFile) throws Exception {
        Class.forName("org.sqlite.JDBC");
        SQLiteConfig config = new SQLiteConfig();
        config.setReadOnly(true);
        return DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath(), config.toProperties());
    }
}
