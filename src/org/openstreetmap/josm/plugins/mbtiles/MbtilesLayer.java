package org.openstreetmap.josm.plugins.mbtiles;

import static org.openstreetmap.josm.tools.I18n.tr;

import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.imagery.ImageryInfo;
import org.openstreetmap.josm.data.imagery.ImageryInfo.ImageryBounds;
import org.openstreetmap.josm.data.imagery.ImageryInfo.ImageryType;
import org.openstreetmap.josm.data.osm.visitor.BoundingXYVisitor;
import org.openstreetmap.josm.gui.layer.TMSLayer;

/**
 * Class that displays a slippy map layer. Adapted from SlippyMap plugin for mbtiles use.
 *
 * @author Ian Dees <ian.dees@gmail.com>
 *
 */
public class MbtilesLayer extends TMSLayer {
    /**
     * Actual zoom lvl. Initial zoom lvl is set to
     * {@link FieldPapersPreferences#getMinZoomLvl()}.
     */
    private int currentZoomLevel;

    private Bounds printBounds;
    private String fieldPapersId;

    public MbtilesLayer(String id, String tileUrlTemplate, Bounds b, int minz, int maxz) {
        super(buildImageryInfo(id, tileUrlTemplate, b, minz, maxz));
        this.fieldPapersId = id;

        this.printBounds = b;
        this.currentZoomLevel = minz;
    }

    private static ImageryInfo buildImageryInfo(String id, String tileUrlTemplate, Bounds b, int minz, int maxz) {
        ImageryInfo info = new ImageryInfo(tr("Field Papers: {0}", id), tileUrlTemplate);
        info.setBounds(new ImageryBounds(b.encodeAsString(","), ","));
        info.setDefaultMaxZoom(maxz);
        info.setDefaultMinZoom(minz);
        info.setIcon("fieldpapers");
        info.setImageryType(ImageryType.TMS);
        return info;
    }

    @Override
    public Object getInfoComponent() {
        return getToolTipText();
    }

    @Override
    public String getToolTipText() {
        return tr("Field Papers layer ({0}) in zoom {1}", this.getFieldPapersId(), currentZoomLevel);
    }

    @Override
    public void visitBoundingBox(BoundingXYVisitor v) {
        if (printBounds != null)
            v.visit(printBounds);
    }

    public String getFieldPapersId() {
        return fieldPapersId;
    }

}
