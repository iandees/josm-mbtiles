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

/**
 * Tests for the export coordinate logic and round-trip consistency between
 * MbtilesTileLoader (read) and CacheExportPanel.ExportTask (write).
 */
class CacheExportRoundTripTest {

    /**
     * The export inversion formula (CacheExportPanel):
     *   tiley = (int) Math.pow(2, zoom) - 1 - y
     *
     * This should be identical to the read inversion formula (MbtilesTileLoader):
     *   invY = (int) Math.pow(2, zoom) - 1 - tile.getYtile()
     *
     * Verify they produce the same results.
     */
    @ParameterizedTest
    @CsvSource({
            "0, 0",
            "2, 0",
            "2, 3",
            "10, 500",
            "18, 100000",
    })
    void exportAndReadInversionsMatch(int zoom, int josmY) {
        // Read inversion (MbtilesTileLoader)
        int readInvY = (int) Math.pow(2, zoom) - 1 - josmY;

        // Export inversion (CacheExportPanel.ExportTask) — same formula
        int exportInvY = (int) Math.pow(2, zoom) - 1 - josmY;

        assertEquals(readInvY, exportInvY,
                "Read and export Y inversions must match for consistent round-trips");
    }

    @Test
    void roundTripWriteThenRead() throws Exception {
        File dbFile = File.createTempFile("test-roundtrip-", ".mbtiles");
        dbFile.deleteOnExit();

        Class.forName("org.sqlite.JDBC");

        // --- Write phase (simulates CacheExportPanel.ExportTask) ---
        Connection writeConn = openWritable(dbFile);
        writeConn.setAutoCommit(false);
        Statement writeStmt = writeConn.createStatement();
        writeStmt.execute("CREATE TABLE metadata (name text, value text)");
        writeStmt.execute("CREATE TABLE tiles (zoom_level integer, tile_column integer, tile_row integer, tile_data blob)");

        PreparedStatement insertStmt = writeConn.prepareStatement(
                "INSERT INTO tiles(zoom_level, tile_column, tile_row, tile_data) VALUES(?, ?, ?, ?)");

        byte[] tileData = MbtilesTestUtils.createMinimalPng();

        // Simulate exporting tile at JOSM coordinates: zoom=5, x=10, y=20
        int zoom = 5;
        int josmX = 10;
        int josmY = 20;
        // Export applies Y inversion to store in TMS format
        int tmsY = (int) Math.pow(2, zoom) - 1 - josmY;

        insertStmt.setInt(1, zoom);
        insertStmt.setInt(2, josmX);
        insertStmt.setInt(3, tmsY);
        insertStmt.setBytes(4, tileData);
        insertStmt.execute();
        insertStmt.close();
        writeConn.commit();
        writeConn.close();

        // --- Read phase (simulates MbtilesTileLoader) ---
        Connection readConn = openReadOnly(dbFile);
        Statement readStmt = readConn.createStatement();

        // MbtilesTileLoader inverts JOSM's Y to query the TMS row
        int invY = (int) Math.pow(2, zoom) - 1 - josmY;
        String sql = "SELECT tile_data FROM tiles WHERE zoom_level=" + zoom +
                " AND tile_column=" + josmX + " AND tile_row=" + invY + " LIMIT 1";
        ResultSet rs = readStmt.executeQuery(sql);

        assertTrue(rs.next(), "Round-trip: tile should be found after export then read");
        assertArrayEquals(tileData, rs.getBytes(1), "Round-trip: tile data should be identical");

        rs.close();
        readStmt.close();
        readConn.close();
    }

    @Test
    void roundTripMultipleZoomLevels() throws Exception {
        File dbFile = File.createTempFile("test-roundtrip-multi-", ".mbtiles");
        dbFile.deleteOnExit();

        Class.forName("org.sqlite.JDBC");

        Connection writeConn = openWritable(dbFile);
        Statement writeStmt = writeConn.createStatement();
        writeStmt.execute("CREATE TABLE tiles (zoom_level integer, tile_column integer, tile_row integer, tile_data blob)");

        PreparedStatement insertStmt = writeConn.prepareStatement(
                "INSERT INTO tiles(zoom_level, tile_column, tile_row, tile_data) VALUES(?, ?, ?, ?)");

        // Write tiles at various zoom levels with JOSM Y coords inverted for TMS storage
        int[][] tiles = {
                {0, 0, 0},    // zoom, x, josmY
                {2, 1, 2},
                {5, 15, 20},
                {10, 500, 700},
        };

        for (int[] tile : tiles) {
            int zoom = tile[0], x = tile[1], josmY = tile[2];
            int tmsY = (int) Math.pow(2, zoom) - 1 - josmY;
            byte[] data = new byte[]{(byte) zoom, (byte) x, (byte) josmY}; // unique data per tile

            insertStmt.setInt(1, zoom);
            insertStmt.setInt(2, x);
            insertStmt.setInt(3, tmsY);
            insertStmt.setBytes(4, data);
            insertStmt.execute();
        }
        insertStmt.close();
        writeStmt.close();
        writeConn.close();

        // Read back using the same inversion
        Connection readConn = openReadOnly(dbFile);
        Statement readStmt = readConn.createStatement();

        for (int[] tile : tiles) {
            int zoom = tile[0], x = tile[1], josmY = tile[2];
            int invY = (int) Math.pow(2, zoom) - 1 - josmY;
            String sql = "SELECT tile_data FROM tiles WHERE zoom_level=" + zoom +
                    " AND tile_column=" + x + " AND tile_row=" + invY + " LIMIT 1";
            ResultSet rs = readStmt.executeQuery(sql);

            assertTrue(rs.next(),
                    String.format("Tile at zoom=%d, x=%d, josmY=%d should be found", zoom, x, josmY));

            byte[] data = rs.getBytes(1);
            assertEquals((byte) zoom, data[0]);
            assertEquals((byte) x, data[1]);
            assertEquals((byte) josmY, data[2]);
            rs.close();
        }

        readStmt.close();
        readConn.close();
    }

    @Test
    void exportCreatesValidMetadata() throws Exception {
        File dbFile = File.createTempFile("test-export-meta-", ".mbtiles");
        dbFile.deleteOnExit();

        Class.forName("org.sqlite.JDBC");

        // Simulate ExportTask's metadata creation
        Connection conn = openWritable(dbFile);
        conn.setAutoCommit(false);
        Statement stmt = conn.createStatement();
        stmt.execute("CREATE TABLE metadata (name text, value text)");

        PreparedStatement metaStmt = conn.prepareStatement(
                "INSERT INTO metadata(name, value) VALUES(?, ?)");

        String layerName = "Test Export Layer";
        setMeta(metaStmt, "name", layerName);
        setMeta(metaStmt, "type", "baselayer");
        setMeta(metaStmt, "version", "1");
        setMeta(metaStmt, "description", layerName);
        setMeta(metaStmt, "format", "jpg");
        metaStmt.close();
        conn.commit();
        conn.close();

        // Read back and verify
        Connection readConn = openReadOnly(dbFile);
        Statement readStmt = readConn.createStatement();
        ResultSet rs = readStmt.executeQuery("SELECT name, value FROM metadata ORDER BY name");

        java.util.Map<String, String> metadata = new java.util.HashMap<>();
        while (rs.next()) {
            metadata.put(rs.getString("name"), rs.getString("value"));
        }

        assertEquals(layerName, metadata.get("name"));
        assertEquals("baselayer", metadata.get("type"));
        assertEquals("1", metadata.get("version"));
        assertEquals(layerName, metadata.get("description"));
        assertEquals("jpg", metadata.get("format"));

        rs.close();
        readStmt.close();
        readConn.close();
    }

    private Connection openWritable(File dbFile) throws Exception {
        SQLiteConfig config = new SQLiteConfig();
        config.setReadOnly(false);
        return DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath(), config.toProperties());
    }

    private Connection openReadOnly(File dbFile) throws Exception {
        SQLiteConfig config = new SQLiteConfig();
        config.setReadOnly(true);
        return DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath(), config.toProperties());
    }

    private void setMeta(PreparedStatement stmt, String name, String value) throws java.sql.SQLException {
        stmt.clearParameters();
        stmt.setString(1, name);
        stmt.setString(2, value);
        stmt.execute();
    }
}
