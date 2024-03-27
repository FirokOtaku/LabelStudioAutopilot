package firok.tool.labelstudio.autopilot;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.simplify.TopologyPreservingSimplifier;

import java.util.ArrayList;

public class PolygonSimplifyTests
{
    @Test
    void testJTS()
    {
        var geo = new GeometryFactory();
        var points = new ArrayList<Coordinate>();
        points.add(new Coordinate(0, 0));
        points.add(new Coordinate(0, 1));
        points.add(new Coordinate(0, 2));
        points.add(new Coordinate(0, 3));
        points.add(new Coordinate(0, 4));
        points.add(new Coordinate(0, 5));
        points.add(new Coordinate(100, 5));
        points.add(new Coordinate(100, 100));
        points.add(new Coordinate(50, 50));
        points.add(new Coordinate(0, 0));

        var ring = geo.createLinearRing(points.toArray(new Coordinate[0]));
        var polygon = geo.createPolygon(ring);
        var polygon2 = TopologyPreservingSimplifier.simplify(polygon, 10) instanceof Polygon poly ? poly : null;
        Assertions.assertNotNull(polygon2);
        System.out.println(polygon2);
        Assertions.assertTrue(polygon2.getGeometryN(0).getNumPoints() < polygon.getGeometryN(0).getNumPoints());
    }

    @Test
    void testWheel()
    {
        var wheel = new SimplifyWheel(10);
        var points = new int[] {
                0, 0,
                0, 1,
                0, 2,
                0, 3,
                0, 4,
                0, 5,
                100, 5,
                100, 100,
                50, 50,
        };
        var ret = wheel.simplify(points);
        Assertions.assertNotNull(ret);
        Assertions.assertTrue(ret.length <= points.length);
    }
}
