package org.openstreetmap.josm.plugins.mbtiles.mobac;

import org.openstreetmap.gui.jmapviewer.interfaces.TileLoader;
import org.openstreetmap.gui.jmapviewer.interfaces.TileLoaderListener;
import org.openstreetmap.josm.data.imagery.TileLoaderFactory;

import java.sql.Connection;
import java.util.Map;

public class MobacTileLoaderFactory implements TileLoaderFactory {

    private final Connection connection;

    public MobacTileLoaderFactory(Connection connection) {
        this.connection = connection;
    }

    @Override
    public TileLoader makeTileLoader(TileLoaderListener listener, Map<String, String> headers, long minimumExpiryTime) {
        return new MobacTileLoader(listener, this.connection);
    }
}
