package org.openstreetmap.josm.plugins.mbtiles;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import org.sqlite.SQLiteConfig;

public class SqliteUtils {
	   public static Connection obtainSqliteDbConnection(File dbFile, boolean readOnly) throws SqliteException {
	        try {
	            Class.forName("org.sqlite.JDBC");
	        } catch (ClassNotFoundException e1) {
	            throw new SqliteException("Could not load sqlite driver.", e1);
	        }

	        Connection connection = null;
	        try {
	            SQLiteConfig config = new SQLiteConfig();
	            config.setReadOnly(readOnly);
	            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath(), config.toProperties());
	            return connection;
	        } catch (SQLException e) {
	            throw new SqliteException("Could not connect to sqlite database.", e);
	        }
	    }
}
