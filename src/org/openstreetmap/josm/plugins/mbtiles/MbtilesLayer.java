package org.openstreetmap.josm.plugins.mbtiles;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.openstreetmap.josm.data.imagery.ImageryInfo;
import org.openstreetmap.josm.data.imagery.ImageryInfo.ImageryBounds;
import org.openstreetmap.josm.data.imagery.ImageryInfo.ImageryType;
import org.openstreetmap.josm.gui.layer.TMSLayer;
import org.sqlite.SQLiteConfig;

/**
 * Class that displays a slippy map layer. Adapted from SlippyMap plugin for
 * mbtiles use.
 *
 * @author Ian Dees <ian.dees@gmail.com>
 *
 */
public class MbtilesLayer extends TMSLayer {

    private final Connection connection;

    public MbtilesLayer(File mbtilesFile) throws MbtilesException {
        super(buildImageryInfo(mbtilesFile));
        connection = obtainSqliteDbConnection(mbtilesFile);
        super.tileLoader = new MbtilesTileLoader(this, connection);
    }

    private static Connection obtainSqliteDbConnection(File dbFile) throws MbtilesException {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e1) {
            throw new MbtilesException("Could not load sqlite driver.", e1);
        }

        Connection connection = null;
        try {
            SQLiteConfig config = new SQLiteConfig();
            config.setReadOnly(true);
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath(), config.toProperties());
            return connection;
        } catch (SQLException e) {
            throw new MbtilesException("Could not connect to sqlite database.", e);
        }
    }

    private static ImageryInfo buildImageryInfo(File mbtilesFile) throws MbtilesException {

        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e1) {
            throw new MbtilesException("Could not load sqlite driver.", e1);
        }

        Connection connection = null;

        String version = null;
        String name = null;
        ImageryBounds bounds = null;
        int maxz = 0;
        int minz = 0;

        try {
            SQLiteConfig config = new SQLiteConfig();
            config.setReadOnly(true);
            connection = DriverManager.getConnection("jdbc:sqlite:" + mbtilesFile.getAbsolutePath(), config.toProperties());

            PreparedStatement statement = connection.prepareStatement("SELECT name,value FROM metadata");

            ResultSet rs = statement.executeQuery();
            while (rs.next()) {
                String metaName = rs.getString("name");
                if ("name".equals(metaName)) {
                    name = rs.getString("value");
                } else if ("bounds".equals(metaName)) {
                    // Rearrange the bbox string because it's in Left,Bottom,Right,Top order and
                    // ImageryBounds is expecting Min/Max order.
                    String[] parts = rs.getString("value").split(",");
                    String rearrangedBbox = parts[1] + "," + parts[0] + "," + parts[3] + "," + parts[2];
                    bounds = new ImageryBounds(rearrangedBbox, ",");
                } else if ("minzoom".equals(metaName)) {
                    minz = rs.getInt("value");
                } else if ("maxzoom".equals(metaName)) {
                    maxz = rs.getInt("value");
                } else if ("version".equals(metaName)) {
                    version = rs.getString("value");
                }
            }
        } catch (SQLException e) {
            throw new MbtilesException(tr("This doesn't appear to be a valid mbtiles database."), e);
        } finally {
            try {
                if (connection != null)
                    connection.close();
            } catch (SQLException e) {
                // connection close failed.
                e.printStackTrace();
            }
        }

        if (name == null || maxz == 0 || minz == 0 || bounds == null) {
            throw new MbtilesException(tr("This mbtiles file doesn't have the required metadata for JOSM."));
        }

        ImageryInfo info = new ImageryInfo(tr("MBTiles: {0}", name));
        info.setBounds(bounds);
        info.setDefaultMaxZoom(maxz);
        info.setDefaultMinZoom(minz);
        info.setIcon("mbtiles");
        info.setImageryType(ImageryType.TMS);
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
            e.printStackTrace();
        }
    }

}
