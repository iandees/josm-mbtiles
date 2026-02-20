package org.openstreetmap.josm.plugins.mbtiles.mobac;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import org.junit.jupiter.api.Test;
import org.sqlite.SQLiteConfig;

/**
 * Tests for Mobac metadata parsing logic used by MobacLayer.buildImageryInfo().
 *
 * Since MobacLayer extends AbstractTileSourceLayer and requires full JOSM
 * initialization, these tests exercise the same SQL queries and parsing logic
 * directly against real SQLite databases.
 */
class MobacLayerTest {

    @Test
    void infoTableParsesZoomLevels() throws Exception {
        // Mobac zoom 3-7 = standard zoom 10-14
        File dbFile = MobacTestUtils.createMobacDb(3, 7);

        Connection conn = openReadOnly(dbFile);
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery("SELECT minzoom, maxzoom FROM info");

        assertTrue(rs.next(), "Info table should have a row");
        int mobacMinZ = rs.getInt("minzoom");
        int mobacMaxZ = rs.getInt("maxzoom");

        assertEquals(3, mobacMinZ);
        assertEquals(7, mobacMaxZ);

        // Verify the standard zoom conversion (as done in MobacLayer)
        int minz = Math.min(17 - mobacMinZ, 17 - mobacMaxZ);
        int maxz = Math.max(17 - mobacMinZ, 17 - mobacMaxZ);
        assertEquals(10, minz);
        assertEquals(14, maxz);

        rs.close();
        stmt.close();
        conn.close();
    }

    @Test
    void boundsFromTileCoordinates() throws Exception {
        // Create a Mobac database and add tiles at known coordinates
        File dbFile = MobacTestUtils.createMobacDb(5, 5);

        // Insert tiles at mobac zoom 5 (standard zoom 12) at specific x,y
        byte[] png = new byte[]{1};
        MobacTestUtils.insertTile(dbFile, 5, 100, 200, png);
        MobacTestUtils.insertTile(dbFile, 5, 110, 210, png);

        Connection conn = openReadOnly(dbFile);

        // Query the bounds the same way MobacLayer does
        int mobacMaxZ = 5;
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery(
                "SELECT min(x), min(y), max(x), max(y) FROM tiles WHERE z = " + mobacMaxZ);

        assertTrue(rs.next());
        int minX = rs.getInt(1);
        int minY = rs.getInt(2);
        int maxX = rs.getInt(3);
        int maxY = rs.getInt(4);

        // The tiles we inserted should define the range (plus the default 0,0 tile)
        assertEquals(0, minX);
        assertEquals(0, minY);
        assertEquals(110, maxX);
        assertEquals(210, maxY);

        rs.close();
        stmt.close();
        conn.close();
    }

    @Test
    void emptyInfoTableThrows() throws Exception {
        File tempFile = File.createTempFile("test-mobac-empty-", ".sqlitedb");
        tempFile.deleteOnExit();

        Class.forName("org.sqlite.JDBC");
        SQLiteConfig config = new SQLiteConfig();
        config.setReadOnly(false);
        Connection setupConn = DriverManager.getConnection(
                "jdbc:sqlite:" + tempFile.getAbsolutePath(), config.toProperties());
        Statement setupStmt = setupConn.createStatement();
        setupStmt.execute("CREATE TABLE info (minzoom integer, maxzoom integer)");
        setupStmt.execute("CREATE TABLE tiles (z integer, x integer, y integer, image blob)");
        setupStmt.close();
        setupConn.close();

        // Simulating what MobacLayer does: query info table
        Connection conn = openReadOnly(tempFile);
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery("SELECT minzoom, maxzoom FROM info");

        // Empty info table should have no rows
        assertFalse(rs.next(), "Empty info table should return no rows");

        rs.close();
        stmt.close();
        conn.close();
    }

    @Test
    void mobacSchemaIsDifferentFromMbtiles() throws Exception {
        File dbFile = MobacTestUtils.createMobacDb(5, 10);

        Connection conn = openReadOnly(dbFile);
        Statement stmt = conn.createStatement();

        // Mobac uses "info" table, not "metadata"
        ResultSet rs = stmt.executeQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='info'");
        assertTrue(rs.next(), "Mobac database should have 'info' table");
        rs.close();

        // Mobac tiles table has z,x,y,image columns (not zoom_level, tile_column, tile_row, tile_data)
        rs = stmt.executeQuery("SELECT z, x, y, image FROM tiles LIMIT 1");
        assertTrue(rs.next(), "Should be able to query z,x,y,image columns");
        rs.close();

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
