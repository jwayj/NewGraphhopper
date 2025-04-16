package com.graphhopper.example;
    
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.io.File;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.GraphHopperConfig;
import com.graphhopper.ResponsePath;
import com.graphhopper.config.LMProfile;
import com.graphhopper.config.Profile;

import static com.graphhopper.json.Statement.If;
import static com.graphhopper.json.Statement.Op.LIMIT;
import static com.graphhopper.json.Statement.Op.MULTIPLY;

import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.Snap;
import com.graphhopper.util.CustomModel;
import com.graphhopper.util.DistanceCalc;
import com.graphhopper.util.DistanceCalcEarth;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.Helper;
import com.graphhopper.util.Instruction;
import com.graphhopper.util.InstructionList;
import com.graphhopper.util.Parameters;
import com.graphhopper.util.PointList;
import com.graphhopper.util.Translation;
import com.graphhopper.util.details.PathDetail;
import com.graphhopper.util.shapes.GHPoint;

import java.lang.reflect.Type;
import java.net.InetSocketAddress;

import com.sun.net.httpserver.HttpServer;



    
        
public class RoutingExample {
    public static void main(String[] args) {
        // ✅ 1. 피드백 서버 시작
        FeedbackServer.start();

        // ✅ 2. GeoJSON 파일 생성 (테스트용)
        createTestGeoJsonFile();

        // ✅ 2. 피드백 로드
        Set<Integer> penalizedEdgeIds = new HashSet<>();
        try (Reader reader = new FileReader("example/resources/feedback_log.json")) {
            Gson gson = new Gson();
            Type type = new TypeToken<List<Map<String, List<Map<String, Integer>>>>>() {}.getType();
            List<Map<String, List<Map<String, Integer>>>> feedbackList = gson.fromJson(reader, type);

            for (Map<String, List<Map<String, Integer>>> feedback : feedbackList) {
                for (List<Map<String, Integer>> edgeList : feedback.values()) {
                    for (Map<String, Integer> edgeObj : edgeList) {
                        if (edgeObj.containsKey("edge")) {
                            penalizedEdgeIds.add(edgeObj.get("edge"));
                        }
                    }
                }
            }


            System.out.println("✅ feedback_log.json 에서 penalized edge IDs 로드 완료: " + penalizedEdgeIds);
        } catch (Exception e) {
            System.err.println("❌ feedback_log.json 읽기 실패: " + e.getMessage());
        }

        // ✅ 3. GraphHopper 인스턴스 생성
        double desiredDistance = 5000;
        String relDir = System.getProperty("user.dir") + File.separator;
        GraphHopper hopper = createGraphHopperInstance(relDir + "seoul-non-military.osm.pbf", penalizedEdgeIds);

        // 4. 새로운 SubmitHandler 서버 (8080 포트)
        startRoutingServer(hopper); // 아래 메서드 정의


        // ✅ 5. 고정 출발지/도착지
        GHPoint start = new GHPoint(37.566535, 126.977969); // 시청
        GHPoint end = new GHPoint(37.5581, 126.9458);       // 연세대

        // ✅ 6. routingWithDesiredDistance 실행
        ResponsePath path1 = routingWithDesiredDistance(hopper, desiredDistance, start, end);
        if (path1 != null) {
            // 경로의 거리 출력
            System.out.println("📏 routingWithDesiredDistance 경로 거리: " + path1.getDistance() + " 미터");
        
            // GeoJSON 생성
            String geoJson1 = GeoJsonExporter1.toGeoJSON(path1);
        
            // GeoJSON 데이터가 비어있지 않다면 저장
            if (geoJson1 != null && !geoJson1.isEmpty()) {
                try {
                    SaveGeoJson.saveToFile(geoJson1, "../example/resources/route1.geojson");
                    System.out.println("📂 GeoJSON1 saved to route1.geojson");
                } catch (Exception e) {
                    System.err.println("❌ GeoJSON1 저장 실패: " + e.getMessage());
                }
            } else {
                System.err.println("❌ GeoJSON 데이터가 비어 있습니다.");
            }
        } else {
            System.out.println("❌ 원하는 거리의 경로를 찾을 수 없습니다.");
        }
        

        // ✅ 7. routingWithCircle 실행 (내부에서 route.geojson 저장됨)
        ResponsePath path2 = routingWithCircle(hopper, desiredDistance, start, end);
        if (path2 != null) {
            System.out.println("🌀 routingWithCircle 경로 거리: " + path2.getDistance() + " 미터");
        } else {
            System.out.println("❌ routingWithCircle 경로를 찾을 수 없습니다.");
        }

        //hopper.close();
    }



    public static ResponsePath routingWithDesiredDistance(GraphHopper hopper, double desiredDistance, GHPoint start, GHPoint end) {
        double tolerance = 100; // 100 meters tolerance allowed
        double searchRadius = desiredDistance * 0.75; // search radius set to 75% of the desired distance
        
        ResponsePath bestPath = null;
        double closestDifference = Double.MAX_VALUE;
        
        LocationIndex locationIndex = hopper.getLocationIndex();
        List<GHPoint> nearbyPoints = new ArrayList<>();
        
        // Generate several nearby points within the search radius
        for (int i = 0; i < 10000; i++) { // Increase the number of points to find
            double angle = Math.random() * 2 * Math.PI;
            double distance = Math.random() * searchRadius;
            double lat = start.lat + (distance / 111000) * Math.cos(angle);
            double lon = start.lon + (distance / (111000 * Math.cos(Math.toRadians(start.lat)))) * Math.sin(angle);
            
            Snap qr = locationIndex.findClosest(lat, lon, EdgeFilter.ALL_EDGES);
            if (qr.isValid()) {
                GHPoint nearbyPoint = qr.getSnappedPoint();
                nearbyPoints.add(nearbyPoint);
            }
        }
        
        // Shuffle the points randomly
        Collections.shuffle(nearbyPoints);
        
        // Find paths for each random point
        for (GHPoint intermediatePoint : nearbyPoints) {
            // Request path from start to intermediate point
            ResponsePath path1 = findPath(hopper, start, intermediatePoint);
            // Request path from intermediate point to end
            ResponsePath path2 = findPath(hopper, intermediatePoint, end);
    
            // If both paths are valid, calculate the total distance and check if it matches the desired distance
            if (path1 != null && path2 != null) {
                double totalDistance = path1.getDistance() + path2.getDistance();
                double difference = Math.abs(totalDistance - desiredDistance);
    
                // Keep track of the closest path to the desired distance
                if (difference < closestDifference) {
                    closestDifference = difference;
                    bestPath = combinePaths(path1, path2);
    
                    // If the difference is within the tolerance, return the best path immediately
                    if (difference <= tolerance) {
                        return bestPath;
                    }
                }
            }
        }
    
        // If no valid path is found, fallback to a direct connection path
        if (bestPath == null) {
            System.out.println("Couldn't find a path with the desired distance. Returning direct path.");
            return findPath(hopper, start, end);
        }
    
        System.out.println("Found the closest path with difference: " + closestDifference + " meters");
        return bestPath;
    }
    
    
    static PointList generateRandomWaypoints(GraphHopper hopper, GHPoint start, int numWaypoints, double minDistance, double maxDistance) {
        Random random = new Random();
        LocationIndex locationIndex = hopper.getLocationIndex();
        PointList waypoints = new PointList();
    
        for (int i = 0; i < numWaypoints; i++) {
            for (int attempts = 0; attempts < 100; attempts++) { // 최대 100번 시도
                double distance = minDistance + (maxDistance - minDistance) * random.nextDouble();
                double angle = random.nextDouble() * 2 * Math.PI;
    
                double deltaLat = (distance / 111000) * Math.cos(angle);
                double deltaLon = (distance / (111000 * Math.cos(Math.toRadians(start.lat)))) * Math.sin(angle);
    
                double lat = start.lat + deltaLat;
                double lon = start.lon + deltaLon;
    
                if (Double.isNaN(lat) || Double.isNaN(lon)) continue;
    
                GHPoint candidate = new GHPoint(lat, lon);
                Snap snap = locationIndex.findClosest(candidate.lat, candidate.lon, EdgeFilter.ALL_EDGES);
    
                if (snap.isValid()) {
                    boolean isTooClose = false;
                    GHPoint snappedPoint = snap.getSnappedPoint();
                    
                    for (int j = 0; j < waypoints.size(); j++) {
                        double existingLat = waypoints.getLat(j);
                        double existingLon = waypoints.getLon(j);
                        GHPoint existingPoint = new GHPoint(existingLat, existingLon);
    
                        // calculateDistance 메서드 호출
                        if (calculateDistance(existingPoint, snappedPoint) < minDistance / 2) {
                            isTooClose = true;
                            break;
                        }
                    }
    
                    if (!isTooClose) {
                        waypoints.add(snappedPoint.lat, snappedPoint.lon);
                        break;
                    }
                }
            }
        }
    
        return waypoints;
    } 

    public static ResponsePath findPath(GraphHopper hopper, GHPoint start, GHPoint end) {
        GHRequest req = new GHRequest(start, end)
            .setProfile("foot")
            .setPathDetails(List.of("edge_id"));  // 경로 세부사항에 "edge_id" 포함
    
        GHResponse response = hopper.route(req);
    
        if (response.hasErrors()) {
            System.out.println("❌ 경로 탐색 실패: " + response.getErrors());
            return null;
        }
    
        ResponsePath path = response.getBest();
        if (path != null) {
            // 경로의 edge_id를 활용해 추가 로직 실행
            List<PathDetail> pathDetails = path.getPathDetails().get("edge_id");
            if (pathDetails != null) {
                for (PathDetail detail : pathDetails) {
                    int edgeId = (Integer) detail.getValue();
                    System.out.println("Edge ID: " + edgeId);
                }
            }
        }
        return path;
    }
    

    private static ResponsePath combinePaths(ResponsePath path1, ResponsePath path2) {
        ResponsePath combinedPath = new ResponsePath();
        
        // 포인트 리스트 병합
        PointList combinedPoints = new PointList(path1.getPoints().size() + path2.getPoints().size() - 1, path1.getPoints().is3D());
        combinedPoints.add(path1.getPoints());
        combinedPoints.add(path2.getPoints().copy(1, path2.getPoints().size()));
        combinedPath.setPoints(combinedPoints);
        
        // 거리, 시간, 가중치 합산
        combinedPath.setDistance(path1.getDistance() + path2.getDistance());
        combinedPath.setTime(path1.getTime() + path2.getTime());
        combinedPath.setRouteWeight(path1.getRouteWeight() + path2.getRouteWeight());
        
        // 안내 정보 병합
        InstructionList combinedInstructions = new InstructionList(path1.getInstructions().getTr());
        combinedInstructions.addAll(path1.getInstructions());
        combinedInstructions.addAll(path2.getInstructions());
        combinedPath.setInstructions(combinedInstructions);
        
        // 경로 세부 정보 병합
        Map<String, List<PathDetail>> combinedDetails = new HashMap<>();
        for (Map.Entry<String, List<PathDetail>> entry : path1.getPathDetails().entrySet()) {
            combinedDetails.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        for (Map.Entry<String, List<PathDetail>> entry : path2.getPathDetails().entrySet()) {
            combinedDetails.merge(entry.getKey(), entry.getValue(), (v1, v2) -> {
                v1.addAll(v2);
                return v1;
            });
        }
        combinedPath.addPathDetails(combinedDetails);


        
        // 기타 필요한 정보 설정
        combinedPath.setAscend(path1.getAscend() + path2.getAscend());
        combinedPath.setDescend(path1.getDescend() + path2.getDescend());
        
        return combinedPath;
    }

    static double calculateDistance(GHPoint point1, GHPoint point2) {
        double earthRadius = 6371000; // 지구 반지름 (미터 단위)
        double dLat = Math.toRadians(point2.lat - point1.lat);
        double dLon = Math.toRadians(point2.lon - point1.lon);
        double lat1 = Math.toRadians(point1.lat);
        double lat2 = Math.toRadians(point2.lat);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(lat1) * Math.cos(lat2) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return earthRadius * c;
    }
    public static GraphHopper createGraphHopperInstance(String osmFilePath, Set<Integer> penalizedEdgeIds) {
        // 캐시 디렉토리 삭제
        File cacheDir = new File("target/routing-graph-cache");
        if (cacheDir.exists()) {
            System.out.println("🧹 기존 캐시 디렉토리 삭제 중...");
            deleteDirectory(cacheDir);
            System.out.println("✅ 캐시 삭제 완료!");
        }
    
        GraphHopper hopper = new GraphHopper();
        
        // OSM 파일 경로 지정
        hopper.setOSMFile(osmFilePath);
        
        // 그래프 저장 경로 설정
        hopper.setGraphHopperLocation("target/routing-graph-cache");

        
        
        // Custom Model 생성 및 penalizedEdgeIds 적용
        CustomModel customModel = createBaseCustomModel(penalizedEdgeIds);
    
        // 프로필 설정
        Profile profile = new Profile("foot")
                .setWeighting("custom")
                .setCustomModel(customModel);
        
        hopper.setProfiles(profile);
        
        // LM 프로필 설정
        hopper.getLMPreparationHandler().setLMProfiles(new LMProfile("foot"));
        
        // 데이터 로드 또는 임포트
        hopper.importOrLoad();
        
        return hopper;
    }
    
    
    
    
    //-----------------여기서부터가 cycle 만들때 필요한 함수 추가(수정)---------------------
    //다양한 경로 생성
    // 🚀 1. 새로운 랜덤 경유지 생성 (더 넓은 범위에서)
    static PointList generateDiverseWaypoints(GraphHopper hopper, GHPoint start, int numWaypoints, double minDistance, double maxDistance) {
        Random random = new Random();
        LocationIndex locationIndex = hopper.getLocationIndex();
        PointList waypoints = new PointList();
        
        List<GHPoint> usedPoints = new ArrayList<>();
    
        for (int i = 0; i < numWaypoints; i++) {
            for (int attempts = 0; attempts < 50; attempts++) {  // 🔥 시도 횟수 줄이기
                double distance = minDistance + (maxDistance - minDistance) * random.nextDouble();
                double angle = random.nextDouble() * 2 * Math.PI;
    
                double deltaLat = (distance / 111000) * Math.cos(angle);
                double deltaLon = (distance / (111000 * Math.cos(Math.toRadians(start.lat)))) * Math.sin(angle);
    
                double lat = start.lat + deltaLat;
                double lon = start.lon + deltaLon;
    
                GHPoint candidate = new GHPoint(lat, lon);
                Snap snap = locationIndex.findClosest(candidate.lat, candidate.lon, EdgeFilter.ALL_EDGES);
    
                if (snap.isValid()) {
                    GHPoint snappedPoint = snap.getSnappedPoint();
                    
                    // 📌 **중복된 지점 회피 + 거리 조건 완화**
                    boolean isValid = true;
                    for (GHPoint used : usedPoints) {
                        double dist = calculateDistance(used, snappedPoint);
                        if (dist < minDistance * 0.8 || dist > maxDistance * 1.2) { // 🔥 오차 허용 범위 추가
                            isValid = false;
                            break;
                        }
                    }
    
                    if (isValid) {
                        waypoints.add(snappedPoint.lat, snappedPoint.lon);
                        usedPoints.add(snappedPoint);
                        break;
                    }
                }
            }
        }
    
        return waypoints;
    }

    // 🚀 2. 경로 탐색 시 동일한 경로 회피 (강제적으로 다른 경로 찾기)
    static ResponsePath findDifferentPath(GraphHopper hopper, GHPoint start, GHPoint end, PointList avoidPoints, List<Integer> avoidEdges) {
        GHRequest request = new GHRequest()
            .addPoint(start)
            .addPoint(end)
            .setProfile("foot")
            .setAlgorithm("astarbi")  // 🔥 CH와 호환되는 알고리즘으로 변경
            .putHint("ch.disable", true)  // 🔥 CH 비활성화
            .setPathDetails(List.of("edge_id"));  // 경로 세부사항에 edge_id 포함

            GHResponse response = hopper.route(request);
    
        if (!avoidPoints.isEmpty()) {
            request.putHint("routing.avoid_points", avoidPoints);
        }
    
        if (!avoidEdges.isEmpty()) {
            request.putHint("routing.avoid_edges", avoidEdges);
            System.out.println("🚧 Avoiding edges: " + avoidEdges);
        }
    
        if (response.hasErrors()) {
            System.err.println("❌ 경로 탐색 오류: " + response.getErrors());
            return null;
        }
    
        ResponsePath bestPath = response.getBest();
        if (bestPath == null || bestPath.getDistance() < 50) { // 🔥 너무 짧은 경로면 다시 시도
            System.out.println("⚠️ 경로가 너무 짧음. 다시 탐색...");
            return null;
        }
    
        // 🔥 **모든 지나온 Edge를 회피하도록 설정 (강력한 회피 적용)**
        List<PathDetail> edgeDetails = bestPath.getPathDetails().getOrDefault("edge_id", new ArrayList<>());
        for (PathDetail detail : edgeDetails) {
            avoidEdges.add((Integer) detail.getValue());
        }
    
        return bestPath;
    }

    // 경로 탐색 함수 수정
    static ResponsePath findDiverseOptimalPath(GraphHopper hopper, GHPoint startPoint, double desiredDistance,
        List<Integer> avoidEdges, PointList avoidPoints, Set<Integer> penalizedEdgeIds, int attemptCount, int maxAttempts) {

        int numWaypoints = 3;
        double minDistance = desiredDistance * 0.15;
        double maxDistance = desiredDistance * 0.4;
        double lowerBound = desiredDistance * 0.9;
        double upperBound = desiredDistance * 1.1;

        PointList waypoints = generateDiverseWaypoints(hopper, startPoint, numWaypoints, minDistance, maxDistance);
        ResponsePath fullPath = null;
        GHPoint previousPoint = startPoint;
        double totalDistance = 0;

        if (waypoints.isEmpty()) {
            System.out.println("❌ 유효한 경유지를 찾지 못했습니다. 기본 경로를 사용합니다.");
            return findPath(hopper, startPoint, startPoint);
        }

        if (attemptCount >= maxAttempts) {
            System.out.println("❌ 최대 재시도 횟수 초과! 경로 탐색을 종료합니다.");
            return null;
        }

        for (int i = 0; i < waypoints.size(); i++) {
            GHPoint waypoint = new GHPoint(waypoints.getLat(i), waypoints.getLon(i));

            CustomModel customModel = createBaseCustomModel(penalizedEdgeIds);
            for (Integer edgeId : avoidEdges) {
                customModel.addToPriority(If("edge_id == " + edgeId, MULTIPLY, "0.1"));
            }

            GHRequest req = new GHRequest(previousPoint, waypoint)
                .setProfile("foot")
                .setCustomModel(customModel)
                .putHint("ch.disable", true)
                .setPathDetails(List.of("edge_id")); // edge_id를 경로 세부사항에 포함시킴

            GHResponse rsp = hopper.route(req);

            if (rsp.hasErrors()) {
                System.out.println("❌ 경유지 경로 탐색 실패: " + rsp.getErrors());
                continue;
            }

            ResponsePath segment = rsp.getBest();
            boolean hasBlockedEdge = false;

            // 경로 세부사항에서 edge_id를 추출하여 회피 처리
            List<PathDetail> edgeDetails = segment.getPathDetails().getOrDefault("edge_id", List.of());
            for (PathDetail detail : edgeDetails) {
                int edgeId = (Integer) detail.getValue();
                if (penalizedEdgeIds.contains(edgeId) || avoidEdges.contains(edgeId)) {
                    System.out.println("🚫 경로에 회피 또는 패널티 edge 포함됨: " + edgeId);
                    hasBlockedEdge = true;
                    break;
                }
            }

            if (hasBlockedEdge) {
                System.out.println("⏭️ 해당 경로는 무시합니다.");
                continue;
            }

            fullPath = (fullPath == null) ? segment : combinePaths(fullPath, segment);
            totalDistance += segment.getDistance();

            segment.getPathDetails().getOrDefault("edge_id", new ArrayList<>())
                .forEach(detail -> avoidEdges.add((Integer) detail.getValue()));

            avoidPoints.add(waypoint.lat, waypoint.lon);
            previousPoint = waypoint;
        }

        CustomModel returnModel = createBaseCustomModel(penalizedEdgeIds);
        for (Integer edgeId : avoidEdges) {
            returnModel.addToPriority(If("edge_id == " + edgeId, MULTIPLY, "0.1"));
        }

        GHRequest returnReq = new GHRequest(previousPoint, startPoint)
            .setProfile("foot")
            .setCustomModel(returnModel)
            .putHint("ch.disable", true)
            .setPathDetails(List.of("edge_id"));

        GHResponse returnRsp = hopper.route(returnReq);

        if (!returnRsp.hasErrors()) {
            ResponsePath returnSegment = returnRsp.getBest();

            boolean hasBlockedEdge = false;
            List<PathDetail> edgeDetails = returnSegment.getPathDetails().getOrDefault("edge_id", List.of());

            for (PathDetail detail : edgeDetails) {
                int edgeId = (Integer) detail.getValue();
                if (avoidEdges.contains(edgeId) || penalizedEdgeIds.contains(edgeId)) {
                    System.out.println("🚫 [돌아오는 경로]에 회피 또는 패널티 edge 포함됨: " + edgeId);
                    hasBlockedEdge = true;
                    break;
                }
            }

            if (!hasBlockedEdge) {
                fullPath = (fullPath == null) ? returnSegment : combinePaths(fullPath, returnSegment);
                totalDistance += returnSegment.getDistance();
            } else {
                System.out.println("⏭️ 돌아오는 경로도 무시됨 (회피 대상 포함)");
            }
        }

        if (totalDistance < lowerBound || totalDistance > upperBound) {
            System.out.println("❌ 경로 거리 초과 또는 부족. 다시 탐색...");
            return findDiverseOptimalPath(hopper, startPoint, desiredDistance, avoidEdges, avoidPoints, penalizedEdgeIds, attemptCount + 1, maxAttempts);
        }

        if (fullPath == null) {
            System.out.println("❌ 최종 경로를 찾을 수 없습니다.");
        }
        return fullPath;
    }





    static ResponsePath findAlternativeReturnPath(GraphHopper hopper, GHPoint start, GHPoint end, List<Integer> avoidEdges, PointList avoidPoints) {
        GHRequest request = new GHRequest()
            .addPoint(start)
            .addPoint(end)
            .setProfile("foot")
            .setAlgorithm(Parameters.Algorithms.ALT_ROUTE)  // 🔥 기존 경로를 강하게 회피
            .putHint("ch.disable", true)  // 🔥 CH 비활성화하여 다양한 경로 탐색 가능
            .putHint("alternative_route.max_paths", 3)  // 🔥 최대 3개의 대체 경로 탐색
            .putHint("alternative_route.max_weight_factor", 3.0) // 🔥 최단 경로보다 3배 긴 경로도 허용
    
            // 🔥 지나온 Edge는 강제로 회피하도록 설정
            .putHint("routing.avoid_edges", avoidEdges);
    
        GHResponse response = hopper.route(request);
    
        if (response.hasErrors()) {
            System.out.println("❌ 대체 경로 탐색 실패: " + response.getErrors());
            return null;
        }
    
        return response.getBest();
    }

    static ResponsePath findPathWithWaypoints(GraphHopper hopper, GHPoint start, PointList waypoints, Set<Integer> penalizedEdgeIds) {
        GHRequest req = new GHRequest()
            .setProfile("foot")
            .setAlgorithm(Parameters.Algorithms.ALT_ROUTE)
            .putHint("ch.disable", true)
            .putHint("alternative_route.max_paths", 3)
            .putHint("alternative_route.max_weight_factor", 2.0)
            .addPoint(start)
            .setPathDetails(List.of("edge_id")); // edge_id를 경로에 포함시킴
        
        for (int i = 0; i < waypoints.size(); i++) {
            req.addPoint(new GHPoint(waypoints.getLat(i), waypoints.getLon(i)));
        }
        
        req.addPoint(start); // 다시 시작점으로
        
        GHResponse rsp = hopper.route(req);
        if (rsp.hasErrors()) {
            System.out.println("❌ 경로 탐색 실패: " + rsp.getErrors());
            return null;
        }
        
        ResponsePath path = rsp.getBest();
        
        // 경로에서 `edge_id`를 추출하고 회피 처리
        List<PathDetail> edgeDetails = path.getPathDetails().getOrDefault("edge_id", List.of());
        for (PathDetail detail : edgeDetails) {
            int edgeId = (Integer) detail.getValue();
            if (penalizedEdgeIds.contains(edgeId)) {
                System.out.println("🚫 이 경로에 penalized edge 포함됨: " + edgeId);
                return null; // ❌ 이 경로는 무시
            }
        }
        
        return path;
    }
    
    
   // routingWithCircle 함수 수정
    public static ResponsePath routingWithCircle(GraphHopper hopper, double desiredDistance, GHPoint startPoint, GHPoint endPoint) {
        List<Integer> globalAvoidEdges = new ArrayList<>();
        PointList globalAvoidPoints = new PointList();
        ResponsePath previousPath = null;

        for (int attempt = 0; attempt < 3; attempt++) {
            System.out.println("\uD83D\uDE80 " + (attempt + 1) + "번째 경로 탐색 시작...");

            int attemptCount = 0;  // 시도 횟수
            int maxAttempts = 3;   // 최대 시도 횟수
            
            ResponsePath diversePath = findDiverseOptimalPath(
                hopper, startPoint, desiredDistance, globalAvoidEdges, globalAvoidPoints, new HashSet<>(), attemptCount, maxAttempts
            );
            
            if (diversePath != null) {
                System.out.println("\u2705 최종 경로 거리: " + diversePath.getDistance() + " 미터");

                if (previousPath != null && Math.abs(diversePath.getDistance() - previousPath.getDistance()) < 5) {
                    System.out.println("⚠️ 동일한 경로가 감지됨. 다시 탐색...");
                    continue;
                }

                PointList pathPoints = diversePath.getPoints();
                try {
                    // GeoJSON 생성
                    String geoJsonData = GeoJsonExporter2.toGeoJSON(diversePath, new PointList(), pathPoints);

                    // GeoJSON 데이터가 null이나 빈 문자열이 아닌지 확인
                    if (geoJsonData != null && !geoJsonData.trim().isEmpty()) {
                        // 정상적으로 파일에 저장
                        String filePath = "../example/resources/route.geojson";
                        try {
                            SaveGeoJson.saveToFile(geoJsonData, filePath);
                            System.out.println("GeoJSON saved to " + filePath);
                        } catch (Exception e) {
                            System.err.println("❌ GeoJSON 파일 저장 실패: " + e.getMessage());
                        }
                    } else {
                        System.err.println("❌ GeoJSON 데이터가 비어 있습니다.");
                    }
                } catch (Exception e) {
                    System.err.println("❌ GeoJSON 생성 실패: " + e.getMessage());
                }
            }
        }
        return null; // Default return, you can modify according to your logic
    }

    
    



    //여기부터는 html 연결 관련 메소드

    public static ResponsePath runRouting(GraphHopper hopper, GHPoint start, GHPoint end, double distanceKm, Set<Integer> penalizedEdgeIds) {
        // Create a base custom model
        CustomModel customModel = createBaseCustomModel(penalizedEdgeIds);  // This method is used to apply custom rules for penalized edges
    
        GHRequest request = new GHRequest(start, end)
                .setProfile("foot")
                .setCustomModel(customModel)  // Apply the custom model created above
                .putHint("ch.disable", true)
                .setPathDetails(List.of("edge_id"));  // Include edge_id in path details
    
        GHResponse response = hopper.route(request);
    
        if (response.hasErrors()) {
            throw new RuntimeException("Routing error: " + response.getErrors());
        }
    
        return response.getBest();
    }
    public static void startRoutingServer(GraphHopper hopper) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
    
            server.createContext("/submit", exchange -> {
                // ✅ CORS Preflight 요청 처리
                if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                    exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                    exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "POST, OPTIONS");
                    exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
                    exchange.sendResponseHeaders(204, -1); // No Content
                    return;
                }
    
                // ✅ 본 요청에도 CORS 허용 헤더 추가
                exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
    
                // ✅ SubmitHandler로 처리 위임
                new SubmitHandler(hopper).handle(exchange);
            });
    
            server.setExecutor(null); // 기본 executor 사용
            server.start();
            System.out.println("🚀 Routing server running at http://localhost:8080");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    //커스텀 모델 생성하는 함수 추가
    static CustomModel createBaseCustomModel(Set<Integer> penalizedEdgeIds) {
        CustomModel model = new CustomModel();
        model.addToSpeed(If("true", LIMIT, "5"));
    
        // edge_id가 PathDetail에 포함된 후, 조건을 처리
        for (Integer edgeId : penalizedEdgeIds) {
            // "edge_id"를 사용할 수 없으면 조건을 변경하거나 다른 방법으로 처리해야 함
            model.addToPriority(If("edge_id != null && edge_id == " + edgeId, MULTIPLY, "0.1"));
        }
    
        return model;
    }
    
    


    public static void deleteDirectory(File directory) {
        // Get all files and subdirectories inside the directory
        File[] allContents = directory.listFiles();
        if (allContents != null) {
            // Loop through all the files/subdirectories and delete them recursively
            for (File file : allContents) {
                if (file.isDirectory()) {
                    // Recursively delete subdirectories
                    deleteDirectory(file);
                } else {
                    // Delete the file
                    file.delete();
                }
            }
        }
        // Finally, delete the empty directory
        directory.delete();
    }

    // GeoJSON을 파일에 기댓값을 넣는 부분
    public static void createTestGeoJsonFile() {
        String geoJsonData = "{\n" +
                "\"type\": \"FeatureCollection\",\n" +
                "\"features\": [\n" +
                "  {\n" +
                "    \"type\": \"Feature\",\n" +
                "    \"geometry\": {\n" +
                "      \"type\": \"LineString\",\n" +
                "      \"coordinates\": [[126.9779704135259, 37.566465879937105], [126.9778241, 37.566464], [126.9778258, 37.5664198]]\n" +
                "    },\n" +
                "    \"properties\": {\n" +
                "      \"edge_id\": 231432\n" +
                "    }\n" +
                "  }\n" +
                "]\n" +
                "}";

        try {
            // 파일 경로
            String filePath = "../example/resources/route.geojson";

            // 파일 저장
            SaveGeoJson.saveToFile(geoJsonData, filePath);
            System.out.println("GeoJSON saved to " + filePath);
        } catch (Exception e) {
            System.err.println("❌ GeoJSON 파일 저장 실패: " + e.getMessage());
        }
    }


    
}