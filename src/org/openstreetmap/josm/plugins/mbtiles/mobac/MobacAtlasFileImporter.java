package org.openstreetmap.josm.plugins.mbtiles.mobac;

import org.openstreetmap.josm.actions.ExtensionFileFilter;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.io.importexport.FileImporter;
import org.openstreetmap.josm.gui.progress.ProgressMonitor;
import org.openstreetmap.josm.plugins.mbtiles.SqliteException;
import org.openstreetmap.josm.plugins.mbtiles.SqliteUtils;
import org.openstreetmap.josm.tools.Logging;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;

import static org.openstreetmap.josm.tools.I18n.tr;

public class MobacAtlasFileImporter extends FileImporter {
    public MobacAtlasFileImporter() {
        super(new ExtensionFileFilter("sqlitedb", "sqlitedb", tr("Mobac Atlas") + " (*.sqlitedb)"));
    }

    @Override
    public void importData(File file, ProgressMonitor progressMonitor) throws IOException {
        try {
            Connection conn = SqliteUtils.obtainSqliteDbConnection(file, true);
            MobacLayer layer = new MobacLayer(conn);
            MainApplication.getLayerManager().addLayer(layer);
        } catch (SqliteException e) {
            Logging.logWithStackTrace(Logging.LEVEL_WARN, "Error opening Mbtiles file", e);
            throw new IOException(tr("Opening Mobac Atlas file failed"), e);
        }
    }
}
