package org.openstreetmap.josm.plugins.mbtiles;

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
	public TileLoader makeTileLoader(TileLoaderListener listener) {
		 return makeTileLoader(listener, null);
	}

	@Override
	public TileLoader makeTileLoader(TileLoaderListener listener, Map<String, String> headers) {
		return new MbtilesTileLoader(listener, this.connection);
	}

}