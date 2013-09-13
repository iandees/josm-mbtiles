package org.openstreetmap.josm.plugins.mbtiles;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.event.ActionEvent;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JOptionPane;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.coor.LatLon;

@SuppressWarnings("serial")
public class MbtilesAddLayerAction extends JosmAction {

    public MbtilesAddLayerAction() {
        super(tr("Scanned Map..."), "fieldpapers",
            tr("Display a map that was previously scanned and uploaded to fieldpapers.org"), null, false);
    }

    public void actionPerformed(ActionEvent e) {
        String wpid = JOptionPane.showInputDialog(Main.parent,
            tr("Enter a fieldpapers.org snapshot URL"),
                Main.pref.get("fieldpapers.last-used-id"));

        if (wpid == null || wpid.equals("")) return;

        // Grab id= from the URL if we need to, otherwise get an ID
        String mungedWpId = this.getFieldPapersId(wpid);

        if (mungedWpId == null || mungedWpId.equals("")) return;

        // screen-scrape details about this id from fieldpapers.org
        // e.g. http://fieldpapers.org/snapshot.php?id=nq78w6wl
        String wpUrl = Main.pref.get("fieldpapers.base-url", "http://fieldpapers.org/") + "snapshot.php?id=" + mungedWpId;

        Pattern spanPattern = Pattern.compile("var (\\S*) = (\\S*);");
        Matcher m;

        String boundsStr = "";
        String urlBase = null;
        double north = 0;
        double south = 0;
        double east = 0;
        double west = 0;

        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(new URL(wpUrl).openStream(), "utf-8"));
            for (String line = r.readLine(); line != null; line = r.readLine()) {
                m = spanPattern.matcher(line);
                if (m.find()) {
                    if ("geojpeg_bounds".equals(m.group(1))) boundsStr = m.group(2);
                    else if ("base_provider".equals(m.group(1))) urlBase = m.group(2);
                }
            }
            r.close();
            if (!boundsStr.isEmpty()) {
                String[] boundSplits = boundsStr.replaceAll("\"", "").split(",");
                south = Double.parseDouble(boundSplits[0]);
                west = Double.parseDouble(boundSplits[1]);
                north = Double.parseDouble(boundSplits[2]);
                east = Double.parseDouble(boundSplits[3]);
            }

            if (!urlBase.isEmpty()) {
                urlBase = urlBase.replaceAll("\\\\", "").replaceAll("\"", "");
            }

            if (urlBase == null || (north == 0 && south == 0) || (east == 0 && west == 0)) throw new Exception();
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(Main.parent,tr("Could not read information for the id \"{0}\" from fieldpapers.org", mungedWpId));
            return;
        }

        String tileUrl = urlBase + "/{zoom}/{x}/{y}.jpg";
        Main.pref.put("fieldpapers.last-used-id", mungedWpId);

        Bounds b = new Bounds(new LatLon(south, west), new LatLon(north, east));

        MbtilesLayer wpl = new MbtilesLayer(mungedWpId, tileUrl, b, 13, 18);
        Main.main.addLayer(wpl);

    }

    private String getFieldPapersId(String wpid) {
        if (!wpid.contains("id=")) {
            return wpid;
        } else {
            // To match e.g. http://fieldpapers.org/snapshot.php?id=tz3fq6xl
            // or http://fieldpapers.org/snapshot.php?id=nq78w6wl#15/41.8966/-87.6847
            final Pattern pattern = Pattern.compile("snapshot.php\\?id=([a-z0-9]*)");
            final Matcher matcher = pattern.matcher(wpid);
            final boolean found   = matcher.find();

            if (found) {
                return matcher.group(1);
            }
        }
        return null;
    }
}
