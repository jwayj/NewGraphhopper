package com.graphhopper.example;

import com.graphhopper.ResponsePath;
import com.graphhopper.util.PointList;
import com.graphhopper.util.details.PathDetail;
import com.graphhopper.util.shapes.GHPoint;

import java.util.List;
import java.util.Map;

public class GeoJsonExporter1 {
    public static String toGeoJSON(ResponsePath path) {
        if (path == null) {
            throw new IllegalArgumentException("ResponsePath cannot be null");
        }

        PointList points = path.getPoints();
        Map<String, List<PathDetail>> pathDetails = path.getPathDetails();

        List<PathDetail> edgeDetails = pathDetails.get("edge_id");
        if (edgeDetails == null || edgeDetails.isEmpty()) {
            throw new IllegalStateException("No edge_id path details available");
        }

        StringBuilder geoJson = new StringBuilder();
        geoJson.append("{\"type\": \"FeatureCollection\", \"features\": [");

        for (int i = 0; i < edgeDetails.size(); i++) {
            PathDetail detail = edgeDetails.get(i);
            int from = detail.getFirst();
            int to = detail.getLast();
            int edgeId = (Integer) detail.getValue();

            geoJson.append("{\"type\": \"Feature\", \"geometry\": {\"type\": \"LineString\", \"coordinates\": [");

            for (int j = from; j <= to; j++) {
                GHPoint p = points.get(j);
                geoJson.append("[").append(p.lon).append(", ").append(p.lat).append("]");
                if (j < to) geoJson.append(", ");
            }

            geoJson.append("]}, \"properties\": {\"edge_id\": ").append(edgeId).append("}}");

            if (i < edgeDetails.size() - 1) {
                geoJson.append(", ");
            }
        }

        geoJson.append("]}");
        return geoJson.toString();
    }
}
