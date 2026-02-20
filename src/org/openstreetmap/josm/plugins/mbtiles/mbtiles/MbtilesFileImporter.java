package org.openstreetmap.josm.plugins.mbtiles.mbtiles;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;

import org.openstreetmap.josm.actions.ExtensionFileFilter;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.io.importexport.FileImporter;
import org.openstreetmap.josm.gui.progress.ProgressMonitor;
import org.openstreetmap.josm.plugins.mbtiles.SqliteException;
import org.openstreetmap.josm.plugins.mbtiles.SqliteUtils;
import org.openstreetmap.josm.tools.Logging;

public class MbtilesFileImporter extends FileImporter {

    /**
     * Constructs a new {@code MbtilesFileImporter}.
     */
    public MbtilesFileImporter() {
        super(new ExtensionFileFilter("mbtiles", "mbtiles", tr("MBTiles tilesets") + " (*.mbtiles)"));
    }

    @Override
    public void importData(File file, ProgressMonitor progressMonitor) throws IOException {
        try {
            Connection conn = SqliteUtils.obtainSqliteDbConnection(file, true);
            MbtilesLayer layer = new MbtilesLayer(conn);
            MainApplication.getLayerManager().addLayer(layer);
        } catch (SqliteException e) {
            Logging.logWithStackTrace(Logging.LEVEL_WARN, "Error opening Mbtiles file", e);
            throw new IOException(tr("Opening MBTiles file failed"), e);
        }
    }
}
