package org.openstreetmap.josm.plugins.mbtiles.mbtiles;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Logger;

import org.openstreetmap.gui.jmapviewer.FeatureAdapter;
import org.openstreetmap.gui.jmapviewer.OsmTileLoader;
import org.openstreetmap.gui.jmapviewer.Tile;
import org.openstreetmap.gui.jmapviewer.interfaces.TileJob;
import org.openstreetmap.gui.jmapviewer.interfaces.TileLoaderListener;

public class MbtilesTileLoader extends OsmTileLoader {
    private static final Logger LOG = FeatureAdapter.getLogger(MbtilesTileLoader.class.getCanonicalName());

    private final Connection connection;

    public MbtilesTileLoader(TileLoaderListener listener, Connection conn) {
        super(listener);
        this.connection = conn;
    }

    @Override
    public TileJob createTileLoaderJob(final Tile tile) {
        return new TileJob() {

            @Override
            public void run() {
                try {
                    tile.initLoading();
                    Statement stmt = connection.createStatement();
                    int invY = (int) Math.pow(2, tile.getZoom()) - 1 - tile.getYtile();
                    String sql = "SELECT tile_data FROM tiles WHERE zoom_level="+tile.getZoom()+" AND tile_column="+tile.getXtile()+" AND tile_row="+invY+" LIMIT 1";

                    ResultSet rs = stmt.executeQuery(sql);

                    if(rs.next()) {
                        LOG.fine("Got a row");
                        tile.loadImage(new ByteArrayInputStream(rs.getBytes(1)));
                        tile.setLoaded(true);
                        listener.tileLoadingFinished(tile, true);
                    } else {
                        LOG.fine("No row found");
                        tile.setError("No tile found");
                        listener.tileLoadingFinished(tile, false);
                    }
                    rs.close();
                } catch (SQLException e) {
                    LOG.throwing(this.getClass().getName(), "createTileLoaderJob", e);
                    tile.setError(e.getMessage());
                    listener.tileLoadingFinished(tile, false);
                } catch (IOException e) {
                    LOG.throwing(this.getClass().getName(), "createTileLoaderJob", e);
                    tile.setError(e.getMessage());
                    listener.tileLoadingFinished(tile, false);
                }
            }

            @Override
            public void submit() {
                this.submit(false);
            }

            @Override
            public void submit(boolean force) {
                run();
            }
        };
    }


}
