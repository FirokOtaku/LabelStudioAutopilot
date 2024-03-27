package firok.tool.labelstudio.autopilot;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.simplify.TopologyPreservingSimplifier;

import static firok.topaz.general.Collections.sizeOf;

public class SimplifyWheel
{
    private final int simplifyDistance;
    private final GeometryFactory geo;
    public SimplifyWheel(Config config)
    {
        this(config.getSimplifyDistance());
    }
    public SimplifyWheel(int simplifyDistance)
    {
        this.simplifyDistance = simplifyDistance;
        this.geo = new GeometryFactory();
    }

    /**
     * 聚合简化多边形
     * @param shape 多边形的点列表
     * @return 简化后的多边形点列表
     * */
    public int[] simplify(int[] shape)
    {
        if(simplifyDistance <= 0) return shape;

        var countPoint = sizeOf(shape) / 2;
        var points = new Coordinate[countPoint + 1];
        for(var stepPoint = 0; stepPoint < countPoint; stepPoint++)
        {
            points[stepPoint] = new Coordinate(shape[stepPoint * 2], shape[stepPoint * 2 + 1]);
        }
        points[countPoint] = points[0];

        var ring = geo.createLinearRing(points);
        var poly = geo.createPolygon(ring);
        var polySimplified = TopologyPreservingSimplifier.simplify(poly, simplifyDistance);

        points = polySimplified.getCoordinates();
        countPoint = points.length - 1;
        var ret = new int[countPoint * 2];
        for(var stepPoint = 0; stepPoint < countPoint; stepPoint++)
        {
            ret[stepPoint * 2] = (int) points[stepPoint].getX();
            ret[stepPoint * 2 + 1] = (int) points[stepPoint].getY();
        }
        return ret;
    }
}
