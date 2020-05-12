package org.openstreetmap.josm.plugins.mbtiles;

public class SqliteException extends Throwable {
    public SqliteException(String m) {
        super(m);
    }

    public SqliteException(String m, Throwable e) {
        super(m, e);
    }
}
