package org.openstreetmap.josm.plugins.mbtiles.mbtiles;

import java.sql.Connection;
import java.util.Map;

import org.openstreetmap.gui.jmapviewer.interfaces.TileLoader;
import org.openstreetmap.gui.jmapviewer.interfaces.TileLoaderListener;
import org.openstreetmap.josm.data.imagery.TileLoaderFactory;

public class MbtilesTileLoaderFactory implements TileLoaderFactory {

    private final Connection connection;

    public MbtilesTileLoaderFactory(Connection connection) {
        this.connection = connection;
    }

    @Override
    public TileLoader makeTileLoader(TileLoaderListener listener, Map<String, String> headers, long minimumExpiryTime) {
        return new MbtilesTileLoader(listener, this.connection);
    }
}
