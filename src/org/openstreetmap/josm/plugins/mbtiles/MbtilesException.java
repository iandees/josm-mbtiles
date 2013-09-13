package org.openstreetmap.josm.plugins.mbtiles;


public class MbtilesException extends Exception {

    public MbtilesException(String string) {
        super(string);
    }

    public MbtilesException(String string, Throwable e) {
        super(string, e);
    }

}
