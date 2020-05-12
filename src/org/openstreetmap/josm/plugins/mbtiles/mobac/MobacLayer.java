package org.openstreetmap.josm.plugins.mbtiles.mobac;

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

import static org.openstreetmap.josm.tools.I18n.tr;

public class MobacLayer extends AbstractTileSourceLayer {
    private final Connection connection;

    public MobacLayer(Connection mobacConnection) throws SqliteException {
        super(buildImageryInfo(mobacConnection));
        super.tileLoader = new MobacTileLoader(this, mobacConnection);
        this.connection = mobacConnection;
    }

    private static ImageryInfo buildImageryInfo(Connection mobacConnection) throws SqliteException {
        // Get the filename from the connection
        String name = mobacConnection.toString();

        // Extract minzoom and maxzoom from the database
        int minz, maxz;
        try {
            PreparedStatement statement = mobacConnection.prepareStatement("SELECT minzoom, maxzoom FROM info");

            ResultSet rs = statement.executeQuery();
            if (!rs.next()) {
                throw new SqliteException("No metadata found in Mobac Atlas");
            }
            minz = rs.getInt("minzoom");
            maxz = rs.getInt("maxzoom");
            rs.close();
        } catch (SQLException e) {
            throw new SqliteException("Couldn't read metadata from Mobac Atlas", e);
        }

        // Use the data from the metadata table to build the ImageryInfo object
        ImageryInfo info = new ImageryInfo(tr("Mobac Atlas: {0}", name));
        info.setDefaultMaxZoom(maxz);
        info.setDefaultMinZoom(minz);
        info.setIcon("mbtiles");
        info.setImageryType(ImageryInfo.ImageryType.TMS);
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
