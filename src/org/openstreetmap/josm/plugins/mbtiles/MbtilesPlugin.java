package org.openstreetmap.josm.plugins.mbtiles;

import javax.swing.JMenu;

import org.openstreetmap.josm.actions.ExtensionFileFilter;
import org.openstreetmap.josm.plugins.Plugin;
import org.openstreetmap.josm.plugins.PluginInformation;

/**
 * Main class for the mbtiles plugin.
 *
 * @author Ian Dees <ian.dees@gmail.com>
 *
 */
public class MbtilesPlugin extends Plugin
{
    static JMenu walkingPapersMenu;

    public MbtilesPlugin(PluginInformation info)
    {
        super(info);
        ExtensionFileFilter.importers.add(new MbtilesFileImporter());
        ExtensionFileFilter.updateAllFormatsImporter();
    }
}
