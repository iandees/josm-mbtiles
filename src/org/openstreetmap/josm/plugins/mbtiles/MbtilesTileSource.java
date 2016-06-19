package org.openstreetmap.josm.plugins.mbtiles;

import java.awt.Point;

import org.openstreetmap.gui.jmapviewer.TileXY;
import org.openstreetmap.gui.jmapviewer.interfaces.ICoordinate;
import org.openstreetmap.gui.jmapviewer.tilesources.AbstractTMSTileSource;
import org.openstreetmap.gui.jmapviewer.tilesources.TileSourceInfo;

public class MbtilesTileSource extends AbstractTMSTileSource {

    public MbtilesTileSource(TileSourceInfo info) {
        super(info);
    }

    @Override
    public double getDistance(double la1, double lo1, double la2, double lo2) {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public Point latLonToXY(double lat, double lon, int zoom) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public ICoordinate xyToLatLon(int x, int y, int zoom) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public TileXY latLonToTileXY(double lat, double lon, int zoom) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public ICoordinate tileXYToLatLon(int x, int y, int zoom) {
        // TODO Auto-generated method stub
        return null;
    }

}
