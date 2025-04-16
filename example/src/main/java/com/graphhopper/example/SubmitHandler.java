package com.graphhopper.example;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.graphhopper.GraphHopper;
import com.graphhopper.ResponsePath;
import com.graphhopper.util.shapes.GHPoint;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import com.sun.net.httpserver.Headers;

public class SubmitHandler implements HttpHandler {

    private final GraphHopper hopper;

    // GraphHopper 인스턴스를 생성자에서 주입받음
    public SubmitHandler(GraphHopper hopper) {
        this.hopper = hopper;
    }

    @Override
public void handle(HttpExchange exchange) throws IOException {
    if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
        exchange.sendResponseHeaders(405, -1); // Method Not Allowed
        return;
    }

    try (InputStream is = exchange.getRequestBody()) {
        String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        System.out.println("받은 요청 본문: " + body);  // 요청 본문 출력

        JSONObject json = new JSONObject(body);

        String startAddr = json.getString("startAddress");
        String endAddr = json.getString("endAddress");
        double distance = Double.parseDouble(json.getString("distance"));
        String slope = json.optString("slope", "medium");

        // 요청 정보 로그 출력
        System.out.println("요청 받은 정보:");
        System.out.println("출발지: " + startAddr);
        System.out.println("도착지: " + endAddr);
        System.out.println("거리: " + distance + "km");
        System.out.println("선택한 경사도 옵션: " + slope);
        
        // 경로 계산 코드
        GHPoint start = geocode(startAddr);
        GHPoint end = geocode(endAddr);

        // 기타 경로 탐색 및 GeoJSON 저장 로직...
    } catch (Exception e) {
        e.printStackTrace();
        exchange.sendResponseHeaders(500, -1); // Internal Server Error
    } finally {
        exchange.close();
    }
}



    // 주소를 지오코딩하여 GHPoint 반환
    private GHPoint geocode(String address) throws Exception {
        String apiKey = "1f8f60dd53fa443dd96264842ac6c3aa"; // REST API 키
        String encoded = URLEncoder.encode(address, "UTF-8");
        String url = "https://dapi.kakao.com/v2/local/search/address.json?query=" + encoded;

        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "KakaoAK " + apiKey);

        int code = conn.getResponseCode();
        System.out.println("👉 요청 URL: " + url);
        System.out.println("👉 응답 코드: " + code);

        if (code != 200) {
            BufferedReader err = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
            String errorMsg = err.lines().reduce("", (a, b) -> a + b);
            System.out.println("❌ Kakao 응답 에러 메시지: " + errorMsg);
            throw new RuntimeException("카카오 주소 검색 실패: " + errorMsg);
        }

        try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            String response = br.lines().reduce("", (acc, line) -> acc + line);
            JSONObject json = new JSONObject(response);
            JSONObject doc = json.getJSONArray("documents").getJSONObject(0);
            double lat = Double.parseDouble(doc.getString("y"));
            double lon = Double.parseDouble(doc.getString("x"));
            return new GHPoint(lat, lon);
        }
    }
}
