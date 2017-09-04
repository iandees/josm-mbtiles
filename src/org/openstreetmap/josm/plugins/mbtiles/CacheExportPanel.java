package org.openstreetmap.josm.plugins.mbtiles;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map.Entry;
import java.util.Set;

import javax.swing.AbstractAction;
import javax.swing.JFileChooser;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.filechooser.FileFilter;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;

import org.apache.commons.jcs.access.CacheAccess;
import org.openstreetmap.josm.actions.DiskAccessAction;
import org.openstreetmap.josm.data.cache.BufferedImageCacheEntry;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.PleaseWaitRunnable;
import org.openstreetmap.josm.gui.layer.TMSLayer;
import org.openstreetmap.josm.gui.preferences.PreferenceTabbedPane;
import org.openstreetmap.josm.gui.preferences.SubPreferenceSetting;
import org.openstreetmap.josm.gui.preferences.TabPreferenceSetting;
import org.openstreetmap.josm.gui.preferences.imagery.CacheContentsPanel;
import org.openstreetmap.josm.gui.widgets.AbstractFileChooser;
import org.openstreetmap.josm.gui.widgets.ButtonColumn;
import org.openstreetmap.josm.tools.GBC;
import org.openstreetmap.josm.tools.Logging;


public class CacheExportPanel implements SubPreferenceSetting {

    private static final class CacheExportPanelTableModel extends DefaultTableModel {
        private static final long serialVersionUID = -5216104564896760787L;

        private CacheExportPanelTableModel(Object[][] data, Object[] columnNames) {
            super(data, columnNames);
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return column == 2;
        }
    }

    private final class CacheExportAction extends AbstractAction {
        private static final long serialVersionUID = -7936964845553915822L;
        private final CacheAccess<String, BufferedImageCacheEntry> cache;
        private final JTable ret;

        private CacheExportAction(CacheAccess<String, BufferedImageCacheEntry> cache, JTable ret) {
            this.cache = cache;
            this.ret = ret;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            int row = ret.convertRowIndexToModel(ret.getEditingRow());
            String layerName = ret.getValueAt(row, 0).toString();

            AbstractFileChooser fileDialog = DiskAccessAction.createAndOpenFileChooser(
                    false, 
                    false, 
                    tr("Save layer {0} as...", layerName),
                    new FileFilter() {
                        @Override
                        public boolean accept(File f) {
                            return f.isDirectory() || f.getName().endsWith(".mbtiles");
                        }

                        @Override
                        public String getDescription() {
                            return "MbTiles file (*.mbtiles)";
                        }
                    },
                    JFileChooser.FILES_AND_DIRECTORIES, 
                    null);
            if (fileDialog == null) {
                return; // user canceled operation
            }
            File saveFile = fileDialog.getSelectedFile();

            MainApplication.worker.execute(new ExportTask(cache, layerName, saveFile));
        }
    }

    @Override
    public void addGui(PreferenceTabbedPane gui) {
        JPanel panel = new JPanel();
        panel.setLayout(new GridBagLayout());
        CacheAccess<String, BufferedImageCacheEntry> cache = TMSLayer.getCache();
        DefaultTableModel model = getTableModel(cache);
        panel.add(getTableForCache(cache, model), GBC.eol().fill(GBC.BOTH));
        getTabPreferenceSetting(gui).addSubTab(this, "Export MbTiles", new JScrollPane(panel));
    }

    @Override
    public boolean ok() {
        // restart not required
        return false;
    }

    @Override
    public boolean isExpert() {
        return false;
    }

    @Override
    public TabPreferenceSetting getTabPreferenceSetting(PreferenceTabbedPane gui) {
        return gui.getImageryPreference();
    }


    private JTable getTableForCache(final CacheAccess<String, BufferedImageCacheEntry> cache, final TableModel tableModel) {
        final JTable ret = new JTable(tableModel);
        ButtonColumn buttonColumn = new ButtonColumn(new CacheExportAction(cache, ret), "Export");
        TableColumn tableColumn = ret.getColumnModel().getColumn(2);
        tableColumn.setCellRenderer(buttonColumn);
        tableColumn.setCellEditor(buttonColumn);
        return ret;
    }

    private static DefaultTableModel getTableModel(final CacheAccess<String, BufferedImageCacheEntry> cache) {
        return new CacheExportPanelTableModel(CacheContentsPanel.getCacheStats(cache), new String[]{tr("Cache name"), tr("Object Count"), tr("Export")});
    }

    static class ExportTask extends PleaseWaitRunnable {
        private String layerName;
        private CacheAccess<String, BufferedImageCacheEntry> cache;
        private File saveFile;
        private boolean cancel = false;

        public ExportTask(CacheAccess<String, BufferedImageCacheEntry> cache, String layerName, File saveFile) {
            super(tr("Exporing {0} cache files", layerName));
            this.layerName = layerName;
            this.cache = cache;
            this.saveFile = saveFile;
        }


        @Override
        protected void realRun() throws IOException {
            if (saveFile.exists()) {
                if (!saveFile.delete()) {
                    throw new IOException("Unable to delete file: " + saveFile.getAbsolutePath());
                }
            }
            Connection connection = null;
            try {
                long startTime = System.currentTimeMillis();
                connection = MbTilesUtils.obtainSqliteDbConnection(saveFile, false);
                connection.setAutoCommit(false);
                Statement stmt = connection.createStatement();
                stmt.execute("CREATE TABLE metadata (name text, value text)");
                PreparedStatement metadataStmt = connection.prepareStatement("INSERT INTO metadata(name, value) VALUES(:1, :2);");
                setMetadata(metadataStmt, "name", layerName);
                setMetadata(metadataStmt, "type", "baselayer");
                setMetadata(metadataStmt, "version", "1");
                setMetadata(metadataStmt, "description", layerName);
                setMetadata(metadataStmt, "format", "jpg");		
                stmt.execute("CREATE TABLE tiles (zoom_level integer, tile_column integer, tile_row integer, tile_data blob);");
                metadataStmt.close();
                PreparedStatement insertStmt = connection.prepareStatement("INSERT INTO tiles(zoom_level, tile_column, tile_row, tile_data) values(:1, :2, :3, :4);");

                Set<Entry<String, BufferedImageCacheEntry>> matching = cache.getMatching("^" + layerName + ".*$").entrySet();
                progressMonitor.setTicksCount(matching.size());

                for(Entry<String, BufferedImageCacheEntry> entry: matching) {
                    insertStmt.clearParameters();
                    String key = entry.getKey();
                    String[] parts = key.split("/");
                    int len = parts.length;
                    int zoom = Integer.parseInt(parts[len-3]);
                    int tilex = Integer.parseInt(parts[len-2]);
                    int tiley = (int) Math.pow(2, zoom) - 1 - Integer.parseInt(parts[len-1]);								
                    insertStmt.setInt(1, zoom);
                    insertStmt.setInt(2, tilex);
                    insertStmt.setInt(3, tiley);				
                    insertStmt.setBytes(4, entry.getValue().getContent()); //setBlob is not supported
                    insertStmt.execute();                    
                    progressMonitor.worked(1);
                    if (cancel ) {
                        saveFile.delete();
                        return;
                    }
                }
                connection.commit();
                connection.close();
                Logging.info("MbTiles export took: " + (System.currentTimeMillis() - startTime) + " ms");
            } catch (Exception e) {
                saveFile.delete();			
                throw new IOException(e);
            } finally {
                if (connection != null) {
                    try {
                        connection.close();
                    } catch (SQLException e) {
                        Logging.logWithStackTrace(Logging.LEVEL_WARN, "Error closing sqlite database", e);
                    }
                }
            }
        }

        private void setMetadata(PreparedStatement stmt, String name, String value) throws SQLException {
            stmt.clearParameters();
            stmt.setString(1, name);
            stmt.setString(2, value);
            stmt.execute();
        }


        @Override
        protected void cancel() {
            this.cancel = true;
        }

        @Override
        protected void finish() {
            // initentionally left blank

        }
    }

}
