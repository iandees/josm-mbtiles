package org.openstreetmap.josm.plugins.mbtiles;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.zip.CRC32;

import org.sqlite.SQLiteConfig;

/**
 * Test utilities for creating real MBTiles SQLite databases in temp files.
 */
public class MbtilesTestUtils {

    /**
     * Creates a valid MBTiles database file with metadata and sample tiles.
     *
     * @param name     the layer name stored in metadata
     * @param bounds   the bounds string in "left,bottom,right,top" format, or null to omit
     * @param minZoom  the minimum zoom level, or -1 to omit from metadata
     * @param maxZoom  the maximum zoom level, or -1 to omit from metadata
     * @return a temp File containing the MBTiles database
     */
    public static File createTestMbtilesDb(String name, String bounds, int minZoom, int maxZoom) throws Exception {
        File tempFile = File.createTempFile("test-mbtiles-", ".mbtiles");
        tempFile.deleteOnExit();

        Class.forName("org.sqlite.JDBC");
        SQLiteConfig config = new SQLiteConfig();
        config.setReadOnly(false);
        Connection conn = DriverManager.getConnection("jdbc:sqlite:" + tempFile.getAbsolutePath(), config.toProperties());

        try {
            Statement stmt = conn.createStatement();
            stmt.execute("CREATE TABLE metadata (name text, value text)");
            stmt.execute("CREATE TABLE tiles (zoom_level integer, tile_column integer, tile_row integer, tile_data blob)");

            PreparedStatement metaStmt = conn.prepareStatement("INSERT INTO metadata(name, value) VALUES(?, ?)");

            setMeta(metaStmt, "name", name);
            setMeta(metaStmt, "format", "png");

            if (bounds != null) {
                setMeta(metaStmt, "bounds", bounds);
            }
            if (minZoom >= 0) {
                setMeta(metaStmt, "minzoom", String.valueOf(minZoom));
            }
            if (maxZoom >= 0) {
                setMeta(metaStmt, "maxzoom", String.valueOf(maxZoom));
            }
            metaStmt.close();

            // Insert sample tiles at each zoom level
            byte[] pngBytes = createMinimalPng();
            PreparedStatement tileStmt = conn.prepareStatement(
                    "INSERT INTO tiles(zoom_level, tile_column, tile_row, tile_data) VALUES(?, ?, ?, ?)");

            int lo = Math.max(minZoom, 0);
            int hi = maxZoom >= 0 ? maxZoom : 4;
            for (int z = lo; z <= hi; z++) {
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
     * Creates an MBTiles database with only a tiles table (no metadata).
     */
    public static File createTilesOnlyDb(int minZoom, int maxZoom) throws Exception {
        File tempFile = File.createTempFile("test-mbtiles-tilesonly-", ".mbtiles");
        tempFile.deleteOnExit();

        Class.forName("org.sqlite.JDBC");
        SQLiteConfig config = new SQLiteConfig();
        config.setReadOnly(false);
        Connection conn = DriverManager.getConnection("jdbc:sqlite:" + tempFile.getAbsolutePath(), config.toProperties());

        try {
            Statement stmt = conn.createStatement();
            stmt.execute("CREATE TABLE metadata (name text, value text)");
            stmt.execute("CREATE TABLE tiles (zoom_level integer, tile_column integer, tile_row integer, tile_data blob)");

            byte[] pngBytes = createMinimalPng();
            PreparedStatement tileStmt = conn.prepareStatement(
                    "INSERT INTO tiles(zoom_level, tile_column, tile_row, tile_data) VALUES(?, ?, ?, ?)");

            for (int z = minZoom; z <= maxZoom; z++) {
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
     * Inserts a specific tile into an existing MBTiles database file.
     */
    public static void insertTile(File dbFile, int zoom, int col, int row, byte[] data) throws Exception {
        Class.forName("org.sqlite.JDBC");
        SQLiteConfig config = new SQLiteConfig();
        config.setReadOnly(false);
        Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath(), config.toProperties());
        try {
            PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO tiles(zoom_level, tile_column, tile_row, tile_data) VALUES(?, ?, ?, ?)");
            stmt.setInt(1, zoom);
            stmt.setInt(2, col);
            stmt.setInt(3, row);
            stmt.setBytes(4, data);
            stmt.execute();
            stmt.close();
        } finally {
            conn.close();
        }
    }

    /**
     * Creates a minimal valid PNG image (1x1 pixel, red).
     * This is a real PNG that image parsers can decode.
     */
    public static byte[] createMinimalPng() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        // PNG signature
        out.write(new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});

        // IHDR chunk: 1x1, 8-bit RGB
        byte[] ihdrData = new byte[]{
                0, 0, 0, 1,  // width=1
                0, 0, 0, 1,  // height=1
                8,           // bit depth
                2,           // color type: RGB
                0,           // compression
                0,           // filter
                0            // interlace
        };
        writeChunk(out, "IHDR", ihdrData);

        // IDAT chunk: zlib-compressed scanline (filter=0, R=255, G=0, B=0)
        byte[] idatData = new byte[]{
                0x78, 0x01,           // zlib header (CMF, FLG)
                0x01,                 // BFINAL=1, BTYPE=00 (no compression)
                0x04, 0x00,           // LEN=4
                (byte) 0xFB, (byte) 0xFF, // NLEN
                0x00,                 // filter byte: none
                (byte) 0xFF, 0x00, 0x00, // R=255, G=0, B=0
                0x00, 0x05, (byte) 0xFE, 0x02  // Adler-32 checksum
        };
        writeChunk(out, "IDAT", idatData);

        // IEND chunk
        writeChunk(out, "IEND", new byte[0]);

        return out.toByteArray();
    }

    private static void writeChunk(ByteArrayOutputStream out, String type, byte[] data) throws IOException {
        // Length (4 bytes, big-endian)
        int len = data.length;
        out.write((len >> 24) & 0xFF);
        out.write((len >> 16) & 0xFF);
        out.write((len >> 8) & 0xFF);
        out.write(len & 0xFF);

        // Type + Data (for CRC calculation)
        byte[] typeBytes = type.getBytes("US-ASCII");
        out.write(typeBytes);
        out.write(data);

        // CRC32 over type + data
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        long crcVal = crc.getValue();
        out.write((int) ((crcVal >> 24) & 0xFF));
        out.write((int) ((crcVal >> 16) & 0xFF));
        out.write((int) ((crcVal >> 8) & 0xFF));
        out.write((int) (crcVal & 0xFF));
    }

    private static void setMeta(PreparedStatement stmt, String name, String value) throws SQLException {
        stmt.clearParameters();
        stmt.setString(1, name);
        stmt.setString(2, value);
        stmt.execute();
    }
}
