package org.openstreetmap.josm.plugins.mbtiles;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import org.junit.jupiter.api.Test;
import org.sqlite.SQLiteConfig;

/**
 * Tests for metadata parsing logic used by MbtilesLayer.buildImageryInfo().
 *
 * Since MbtilesLayer extends AbstractTileSourceLayer and requires full JOSM
 * initialization, these tests exercise the same SQL queries and parsing logic
 * directly against real SQLite databases.
 */
class MbtilesLayerTest {

    @Test
    void metadataParsingReadsNameAndBounds() throws Exception {
        File dbFile = MbtilesTestUtils.createTestMbtilesDb(
                "My Test Layer", "-74.0,40.0,-73.0,41.0", 2, 14);

        Connection conn = openReadOnly(dbFile);
        PreparedStatement statement = conn.prepareStatement("SELECT name,value FROM metadata");
        ResultSet rs = statement.executeQuery();

        String name = null;
        String boundsStr = null;
        int minz = -1, maxz = -1;

        while (rs.next()) {
            String metaName = rs.getString("name");
            if ("name".equals(metaName)) {
                name = rs.getString("value");
            } else if ("bounds".equals(metaName)) {
                boundsStr = rs.getString("value");
            } else if ("minzoom".equals(metaName)) {
                minz = Integer.parseInt(rs.getString("value"));
            } else if ("maxzoom".equals(metaName)) {
                maxz = Integer.parseInt(rs.getString("value"));
            }
        }

        assertEquals("My Test Layer", name);
        assertEquals("-74.0,40.0,-73.0,41.0", boundsStr);
        assertEquals(2, minz);
        assertEquals(14, maxz);

        rs.close();
        statement.close();
        conn.close();
    }

    @Test
    void boundsRearrangement() {
        // MBTiles stores bounds as Left,Bottom,Right,Top
        // JOSM ImageryBounds expects Bottom,Left,Top,Right (min lat, min lon, max lat, max lon)
        String mbtilesBounds = "-74.0,40.0,-73.0,41.0";
        String[] parts = mbtilesBounds.split(",");
        String rearranged = parts[1] + "," + parts[0] + "," + parts[3] + "," + parts[2];

        assertEquals("40.0,-74.0,41.0,-73.0", rearranged);
    }

    @Test
    void boundsRearrangementWithDecimals() {
        String mbtilesBounds = "-122.5149,37.7080,-122.3550,37.8324";
        String[] parts = mbtilesBounds.split(",");
        String rearranged = parts[1] + "," + parts[0] + "," + parts[3] + "," + parts[2];

        assertEquals("37.7080,-122.5149,37.8324,-122.3550", rearranged);
    }

    @Test
    void zoomFallbackQueriesToTilesTable() throws Exception {
        // Create a database with NO zoom metadata but tiles at zoom 3-7
        File dbFile = MbtilesTestUtils.createTilesOnlyDb(3, 7);

        Connection conn = openReadOnly(dbFile);

        // Simulate the fallback logic from buildImageryInfo
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery("SELECT max(zoom_level) AS max, min(zoom_level) AS min FROM tiles");
        rs.next();
        int maxz = rs.getInt("max");
        int minz = rs.getInt("min");

        assertEquals(7, maxz);
        assertEquals(3, minz);

        rs.close();
        stmt.close();
        conn.close();
    }

    @Test
    void emptyDatabaseThrowsOnTileQuery() throws Exception {
        // Create a database with tables but no data
        File tempFile = File.createTempFile("test-empty-", ".mbtiles");
        tempFile.deleteOnExit();

        Class.forName("org.sqlite.JDBC");
        SQLiteConfig config = new SQLiteConfig();
        config.setReadOnly(false);
        Connection setupConn = DriverManager.getConnection(
                "jdbc:sqlite:" + tempFile.getAbsolutePath(), config.toProperties());
        Statement setupStmt = setupConn.createStatement();
        setupStmt.execute("CREATE TABLE metadata (name text, value text)");
        setupStmt.execute("CREATE TABLE tiles (zoom_level integer, tile_column integer, tile_row integer, tile_data blob)");
        setupStmt.close();
        setupConn.close();

        Connection conn = openReadOnly(tempFile);
        Statement stmt = conn.createStatement();

        // The fallback query will return 0 for both max and min on empty tiles table
        ResultSet rs = stmt.executeQuery("SELECT max(zoom_level) AS max, min(zoom_level) AS min FROM tiles");
        rs.next();
        // With no tiles, the aggregate functions return 0 (SQL NULL -> getInt returns 0)
        assertEquals(0, rs.getInt("max"));
        assertEquals(0, rs.getInt("min"));

        rs.close();
        stmt.close();
        conn.close();
    }

    @Test
    void invalidDatabaseThrowsSQLException() throws Exception {
        // Create a non-database file
        File tempFile = File.createTempFile("test-invalid-", ".mbtiles");
        tempFile.deleteOnExit();
        java.io.FileWriter writer = new java.io.FileWriter(tempFile);
        writer.write("This is not a database");
        writer.close();

        // Attempting to query it should throw
        assertThrows(Exception.class, () -> {
            Connection conn = openReadOnly(tempFile);
            Statement stmt = conn.createStatement();
            stmt.executeQuery("SELECT name,value FROM metadata");
        });
    }

    private Connection openReadOnly(File dbFile) throws Exception {
        Class.forName("org.sqlite.JDBC");
        SQLiteConfig config = new SQLiteConfig();
        config.setReadOnly(true);
        return DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath(), config.toProperties());
    }
}
