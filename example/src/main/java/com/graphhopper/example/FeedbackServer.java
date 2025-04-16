package com.graphhopper.example;

import static spark.Spark.*;

import java.io.FileWriter;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class FeedbackServer {

    public static void main(String[] args) {
        start();
    }

    public static void start() {
        // ✅ 정적 파일 제공 경로 설정 (HTML, GeoJSON 모두 포함될 폴더)
        // 예: index.html, route1.geojson, route.geojson 등이 이 폴더에 있음
        staticFiles.externalLocation("C:/Users/dabii/NewGraphhopper/example/resources"); //절대경로


        // ✅ 포트 설정 (4567 포트에서 실행)
        port(4567);

        post("/submit", (req, res) -> {
            String body = req.body();
            System.out.println("📨 라우팅 요청 수신: " + body);
        
            // 여기에서 startAddress, endAddress, distance, slope 등 처리하면 됨
            // 예: GeoCoding → 좌표 변환 → GHRequest 만들기 → route.geojson 저장
        
            // 지금은 응답만 주기 (테스트용)
            return "경로 요청 처리 완료!";
        });
        

        // ✅ 피드백 전송 처리 (optional: 지금은 안 써도 됨)
        post("/feedback", (req, res) -> {
            String body = req.body();
            System.out.println("📥 받은 피드백: " + body);

            try {
                Gson gson = new Gson();
                Map<String, List<String>> feedbackMap = gson.fromJson(
                    body, new TypeToken<Map<String, List<String>>>() {}.getType()
                );

                List<String> selectedEdges = feedbackMap.get("selectedEdges");

                Map<String, List<Integer>> cleanedMap = new LinkedHashMap<>();
                List<Integer> parsedEdges = selectedEdges.stream()
                    .map(s -> {
                        try {
                            return Integer.parseInt(s.replaceAll("[^0-9]", ""));
                        } catch (Exception e) {
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

                cleanedMap.put("selectedEdges", parsedEdges);

                // 피드백 저장
                LogGeoJson.writeFeedback(cleanedMap);
                System.out.println("✅ feedback_log.json 작성 완료!");

                return "피드백 수신 완료!";
            } catch (Exception e) {
                e.printStackTrace();
                res.status(500);
                return "서버 에러: " + e.getMessage();
            }
        });

        System.out.println("✅ FeedbackServer is running at: http://localhost:4567");
    }
}
