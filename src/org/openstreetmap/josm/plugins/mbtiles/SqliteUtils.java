package org.openstreetmap.josm.plugins.mbtiles;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import org.sqlite.SQLiteConfig;

public class SqliteUtils {
	   public static Connection obtainSqliteDbConnection(File dbFile, boolean readOnly) throws SqliteException {
	        // Temporarily set the context classloader to the plugin's classloader so that
	        // sqlite-jdbc's JNI native library can find all required classes (e.g. org.sqlite.Collation)
	        // when running inside JOSM's PluginClassLoader environment.
	        Thread currentThread = Thread.currentThread();
	        ClassLoader originalClassLoader = currentThread.getContextClassLoader();
	        currentThread.setContextClassLoader(SqliteUtils.class.getClassLoader());
	        try {
	            Class.forName("org.sqlite.JDBC");

	            SQLiteConfig config = new SQLiteConfig();
	            config.setReadOnly(readOnly);
	            return DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath(), config.toProperties());
	        } catch (ClassNotFoundException e1) {
	            throw new SqliteException("Could not load sqlite driver.", e1);
	        } catch (SQLException e) {
	            throw new SqliteException("Could not connect to sqlite database.", e);
	        } finally {
	            currentThread.setContextClassLoader(originalClassLoader);
	        }
	    }
}
