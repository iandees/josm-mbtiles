package org.openstreetmap.josm.plugins.mbtiles.mobac;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

import org.openstreetmap.josm.plugins.mbtiles.MbtilesTestUtils;
import org.sqlite.SQLiteConfig;

/**
 * Test utilities for creating Mobac atlas SQLite databases.
 *
 * Mobac databases have a different schema than MBTiles:
 * - "info" table with minzoom, maxzoom columns (not "metadata")
 * - "tiles" table with z, x, y, image columns (not zoom_level, tile_column, tile_row, tile_data)
 * - Zoom levels are inverted: mobacZoom = 17 - standardZoom
 * - Y coordinates are NOT inverted (same as web/slippy map convention)
 */
public class MobacTestUtils {

    /**
     * Creates a valid Mobac atlas database with info and tiles tables.
     *
     * @param mobacMinZoom Mobac-style min zoom (17 - standardMax)
     * @param mobacMaxZoom Mobac-style max zoom (17 - standardMin)
     * @return a temp File containing the Mobac database
     */
    public static File createMobacDb(int mobacMinZoom, int mobacMaxZoom) throws Exception {
        File tempFile = File.createTempFile("test-mobac-", ".sqlitedb");
        tempFile.deleteOnExit();

        Class.forName("org.sqlite.JDBC");
        SQLiteConfig config = new SQLiteConfig();
        config.setReadOnly(false);
        Connection conn = DriverManager.getConnection("jdbc:sqlite:" + tempFile.getAbsolutePath(), config.toProperties());

        try {
            Statement stmt = conn.createStatement();
            stmt.execute("CREATE TABLE info (minzoom integer, maxzoom integer)");
            stmt.execute("CREATE TABLE tiles (z integer, x integer, y integer, image blob)");

            PreparedStatement infoStmt = conn.prepareStatement("INSERT INTO info(minzoom, maxzoom) VALUES(?, ?)");
            infoStmt.setInt(1, mobacMinZoom);
            infoStmt.setInt(2, mobacMaxZoom);
            infoStmt.execute();
            infoStmt.close();

            // Insert sample tiles at each Mobac zoom level
            byte[] pngBytes = MbtilesTestUtils.createMinimalPng();
            PreparedStatement tileStmt = conn.prepareStatement(
                    "INSERT INTO tiles(z, x, y, image) VALUES(?, ?, ?, ?)");

            for (int z = mobacMinZoom; z <= mobacMaxZoom; z++) {
                tileStmt.setInt(1, z);
                tileStmt.setInt(2, 0);
                tileStmt.setInt(3, 0);
                tileStmt.setBytes(4, pngBytes);
                tileStmt.execute();
            }
            tileStmt.close();
            stmt.close();
        } finally {
            conn.close();
        }

        return tempFile;
    }

    /**
     * Inserts a tile into a Mobac database.
     */
    public static void insertTile(File dbFile, int z, int x, int y, byte[] data) throws Exception {
        Class.forName("org.sqlite.JDBC");
        SQLiteConfig config = new SQLiteConfig();
        config.setReadOnly(false);
        Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath(), config.toProperties());
        try {
            PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO tiles(z, x, y, image) VALUES(?, ?, ?, ?)");
            stmt.setInt(1, z);
            stmt.setInt(2, x);
            stmt.setInt(3, y);
            stmt.setBytes(4, data);
            stmt.execute();
            stmt.close();
        } finally {
            conn.close();
        }
    }
}
