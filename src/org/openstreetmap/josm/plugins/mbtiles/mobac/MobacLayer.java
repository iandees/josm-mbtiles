package org.openstreetmap.josm.plugins.mbtiles.mobac;

import org.openstreetmap.gui.jmapviewer.FeatureAdapter;
import org.openstreetmap.gui.jmapviewer.OsmMercator;
import org.openstreetmap.gui.jmapviewer.tilesources.AbstractTMSTileSource;
import org.openstreetmap.gui.jmapviewer.tilesources.TMSTileSource;
import org.openstreetmap.josm.data.imagery.ImageryInfo;
import org.openstreetmap.josm.data.imagery.TileLoaderFactory;
import org.openstreetmap.josm.gui.layer.AbstractTileSourceLayer;
import org.openstreetmap.josm.plugins.mbtiles.SqliteException;
import org.openstreetmap.josm.tools.Logging;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.logging.Logger;

import static org.openstreetmap.josm.tools.I18n.tr;

public class MobacLayer extends AbstractTileSourceLayer {
    private static final Logger LOG = FeatureAdapter.getLogger(MobacLayer.class.getCanonicalName());

    private final Connection connection;

    public MobacLayer(Connection mobacConnection) throws SqliteException {
        super(buildImageryInfo(mobacConnection));
        super.tileLoader = new MobacTileLoader(this, mobacConnection);
        this.connection = mobacConnection;
    }

    private static ImageryInfo buildImageryInfo(Connection mobacConnection) throws SqliteException {
        // Get the filename from the connection
        String name = "Mobac Atlas";
        try {
            String uri = mobacConnection.getMetaData().getURL();
            if (uri != null) {
                name = uri.substring(uri.lastIndexOf('/') + 1);
            }
        } catch (SQLException e) {
            Logging.logWithStackTrace(Logging.LEVEL_WARN, "Couldn't get metadata from Mobac Atlas", e);
        }

        // Extract minzoom and maxzoom from the database
        int minz, maxz;
        try {
            PreparedStatement statement = mobacConnection.prepareStatement("SELECT minzoom, maxzoom FROM info");

            ResultSet rs = statement.executeQuery();
            if (!rs.next()) {
                throw new SqliteException("No metadata found in Mobac Atlas");
            }
            int mobacMinZ = rs.getInt("minzoom");
            int mobacMaxZ = rs.getInt("maxzoom");

            // We need to reverse the zoom level to account for Mobac's inverted zoom levels
            minz = Math.min(17 - mobacMinZ, 17 - mobacMaxZ);
            maxz = Math.max(17 - mobacMinZ, 17 - mobacMaxZ);

            LOG.info("Zoom range: " + minz + " - " + maxz + " (Mobac: " + mobacMinZ + " - " + mobacMaxZ + ")");

            rs.close();
        } catch (SQLException e) {
            throw new SqliteException("Couldn't read metadata from Mobac Atlas", e);
        }

        // Look at the most-zoomed-in tiles to get the bounds
        ImageryInfo.ImageryBounds bounds = null;
        try {
            PreparedStatement statement = mobacConnection.prepareStatement("SELECT min(x), min(y), max(x), max(y) FROM tiles WHERE z = ?");

            // Swap the max zoom back to the value that Mobac expects for this query
            int mobacMaxZ = 17 - maxz;
            statement.setInt(1, mobacMaxZ);
            ResultSet rs = statement.executeQuery();
            if (rs.next()) {
                int x1 = rs.getInt(1);
                int y1 = rs.getInt(4); // y is inverted in Mobac
                int x2 = rs.getInt(3) + 1;
                int y2 = rs.getInt(2);

                LOG.info("Tile range at Mobac zoom " + mobacMaxZ + ": x from " + x1 + " to " + x2 + ", y from " + y1 + " to " + y2);

                // Convert the tile coordinates to lat/lon
                int tileSize = 256;
                double minLon = OsmMercator.MERCATOR_256.xToLon(x1 * tileSize, maxz);
                double minLat = OsmMercator.MERCATOR_256.yToLat(y1 * tileSize, maxz);
                double maxLon = OsmMercator.MERCATOR_256.xToLon(x2 * tileSize, maxz);
                double maxLat = OsmMercator.MERCATOR_256.yToLat(y2 * tileSize, maxz);

                // Build the ImageryBounds using a string
                String boundsString = minLat + "," + minLon + "," + maxLat + "," + maxLon;
                LOG.info("Bounds: " + boundsString);
                bounds = new ImageryInfo.ImageryBounds(boundsString, ",");
            }
            rs.close();
        } catch (SQLException e) {
            Logging.logWithStackTrace(Logging.LEVEL_WARN, "Couldn't read bounds from Mobac Atlas", e);
        }


        // Use the data from the metadata table to build the ImageryInfo object
        ImageryInfo info = new ImageryInfo(tr("Mobac Atlas: {0}", name));
        info.setDefaultMaxZoom(maxz);
        info.setDefaultMinZoom(minz);
        info.setIcon("mbtiles");
        info.setImageryType(ImageryInfo.ImageryType.TMS);
        if (bounds != null) {
            info.setBounds(bounds);
            LOG.info("Setting bounds to " + bounds);
        }
        // Hack around the TMSLayer's URL check
        info.setUrl("tms:http://example.com");
        return info;
    }

    @Override
    public void destroy() {
        super.destroy();

        try {
            connection.close();
        } catch (SQLException e) {
            Logging.logWithStackTrace(Logging.LEVEL_WARN, "Error closing sqlite database", e);
        }
    }

    @Override
    protected TileLoaderFactory getTileLoaderFactory() {
        return new MobacTileLoaderFactory(this.connection);
    }

    @Override
    protected AbstractTMSTileSource getTileSource() {
        return new TMSTileSource(info);
    }

    @Override
    public Collection<String> getNativeProjections() {
        return null;
    }

}
