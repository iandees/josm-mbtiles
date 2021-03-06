package org.openstreetmap.josm.plugins.mbtiles;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.io.File;
import java.io.IOException;

import org.openstreetmap.josm.actions.ExtensionFileFilter;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.io.importexport.FileImporter;
import org.openstreetmap.josm.gui.progress.ProgressMonitor;
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
            MbtilesLayer layer = new MbtilesLayer(file);
            MainApplication.getLayerManager().addLayer(layer);
        } catch (MbtilesException e) {
            Logging.logWithStackTrace(Logging.LEVEL_WARN, "Error importing data", e);
            throw new IOException(tr("Loading MBTiles failed"), e);
        }
    }
}
