package org.openstreetmap.josm.plugins.mbtiles;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.io.File;
import java.io.IOException;

import javax.swing.JOptionPane;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.actions.ExtensionFileFilter;
import org.openstreetmap.josm.gui.progress.ProgressMonitor;
import org.openstreetmap.josm.io.FileImporter;

public class MbtilesFileImporter extends FileImporter {

    public MbtilesFileImporter() {
        super(new ExtensionFileFilter("mbtiles", "mbtiles", tr("MBTiles tilesets") + " (*.mbtiles)"));
    }

    @Override
    public void importData(File file, ProgressMonitor progressMonitor) throws IOException {
        try {
            MbtilesLayer layer = new MbtilesLayer(file);
            Main.main.addLayer(layer);
        } catch (MbtilesException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(Main.panel, tr("Could not load that mbtiles file: {0}", e.getMessage()));
        }
    }

}
