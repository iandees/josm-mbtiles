package org.openstreetmap.josm.plugins.mbtiles;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MbTilesUtilsTest {

    @Test
    void readOnlyConnectionReturnsOpenConnection() throws Exception {
        File dbFile = MbtilesTestUtils.createTestMbtilesDb("test", "-180,-85,180,85", 0, 4);

        Connection conn = SqliteUtils.obtainSqliteDbConnection(dbFile, true);
        assertNotNull(conn);
        assertFalse(conn.isClosed());

        // Verify we can read from it
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery("SELECT count(*) FROM tiles");
        assertTrue(rs.next());
        assertTrue(rs.getInt(1) > 0);
        rs.close();
        stmt.close();
        conn.close();
    }

    @Test
    void writableConnectionAllowsWrites() throws Exception {
        File dbFile = MbtilesTestUtils.createTestMbtilesDb("test", null, 0, 2);

        Connection conn = SqliteUtils.obtainSqliteDbConnection(dbFile, false);
        assertNotNull(conn);

        // Verify we can write
        Statement stmt = conn.createStatement();
        stmt.execute("INSERT INTO metadata(name, value) VALUES('test_key', 'test_value')");
        ResultSet rs = stmt.executeQuery("SELECT value FROM metadata WHERE name='test_key'");
        assertTrue(rs.next());
        assertEquals("test_value", rs.getString(1));
        rs.close();
        stmt.close();
        conn.close();
    }

    @Test
    void readOnlyConnectionToNonexistentFileThrows(@TempDir File tempDir) {
        File nonexistent = new File(tempDir, "does-not-exist.mbtiles");

        // A read-only connection to a nonexistent file should throw
        assertThrows(SqliteException.class, () -> {
            SqliteUtils.obtainSqliteDbConnection(nonexistent, true);
        });
    }

    @Test
    void invalidPathThrowsSqliteException() {
        // A path that truly cannot be opened
        File invalidPath = new File("/nonexistent/deeply/nested/path/that/does/not/exist/db.mbtiles");

        assertThrows(SqliteException.class, () -> {
            SqliteUtils.obtainSqliteDbConnection(invalidPath, false);
        });
    }
}
