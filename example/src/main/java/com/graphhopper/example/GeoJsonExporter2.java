package com.graphhopper.example;

import com.graphhopper.ResponsePath;
import com.graphhopper.util.PointList;
import com.graphhopper.util.details.PathDetail;
import com.graphhopper.util.shapes.GHPoint;

import java.util.List;
import java.util.Map;

public class GeoJsonExporter2 {

    public static String toGeoJSON(ResponsePath path, PointList waypoints, PointList startPoint) {
        if (path == null || path.getPoints().isEmpty()) {
            throw new IllegalArgumentException("경로가 유효하지 않습니다.");
        }
    
        StringBuilder geoJson = new StringBuilder();
        geoJson.append("{\"type\": \"FeatureCollection\", \"features\": [");
    
        // ✅ edge_id 별로 구간 분리해서 feature 생성
        PointList pathPoints = path.getPoints();
        Map<String, List<PathDetail>> pathDetails = path.getPathDetails();
        List<PathDetail> edgeDetails = pathDetails.get("edge_id");
    
        for (int i = 0; i < edgeDetails.size(); i++) {
            PathDetail detail = edgeDetails.get(i);
            int from = detail.getFirst();
            int to = detail.getLast();
            int edgeId = (Integer) detail.getValue();
    
            geoJson.append("{\"type\": \"Feature\", \"geometry\": {\"type\": \"LineString\", \"coordinates\": [");
    
            for (int j = from; j <= to; j++) {
                GHPoint p = pathPoints.get(j);
                geoJson.append("[").append(p.lon).append(", ").append(p.lat).append("]");
                if (j < to) geoJson.append(", ");
            }
    
            geoJson.append("], \"properties\": {\"type\": \"route\", \"edge_id\": ").append(edgeId).append("}}");
    
            if (i < edgeDetails.size() - 1 || waypoints.size() > 0 || startPoint.size() > 0) {
                geoJson.append(", ");
            }
        }
    
        // ✅ 경유지 (Point) 추가
        for (int i = 0; i < waypoints.size(); i++) {
            double lat = waypoints.getLat(i);
            double lon = waypoints.getLon(i);
    
            geoJson.append("{\"type\": \"Feature\", \"geometry\": {\"type\": \"Point\", \"coordinates\": [")
                   .append(lon).append(", ").append(lat)
                   .append("], \"properties\": {\"type\": \"waypoint\", \"index\": ").append(i).append("}}");
    
            if (i < waypoints.size() - 1 || startPoint.size() > 0) {
                geoJson.append(", ");
            }
        }
    
        // ✅ 출발/도착지 (Point) 추가 (하나의 Point만 추가되도록 수정)
        if (startPoint.size() > 0) {
            double lat = startPoint.getLat(0);  // 시작점
            double lon = startPoint.getLon(0);  // 시작점
    
            geoJson.append("{\"type\": \"Feature\", \"geometry\": {\"type\": \"Point\", \"coordinates\": [")
                   .append(lon).append(", ").append(lat)
                   .append("], \"properties\": {\"type\": \"start/end\"}}");
        }
    
        geoJson.append("]}");
        return geoJson.toString();
    }
}