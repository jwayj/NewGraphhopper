package com.graphhopper.example;

import com.graphhopper.GraphHopper;
import com.graphhopper.config.Profile;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Collections;

public class RoutingServer {
    public static void main(String[] args) throws Exception {
        // GraphHopper 초기화
        GraphHopper hopper = new GraphHopper();
        hopper.setOSMFile("seoul-non-military.osm.pbf"); // 실제 파일명으로 변경
        hopper.setGraphHopperLocation("target/routing-graph-cache");

        hopper.setProfiles(Collections.singletonList(
            new Profile("foot").setWeighting("fastest") // setTurnCosts 제거
        ));

        hopper.getCHPreparationHandler().setCHProfiles(); // CH 비활성화
        hopper.importOrLoad();

        // HTTP 서버 설정
        int port = 8080;
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        // CORS-safe 핸들러 래핑
        server.createContext("/submit", exchange -> {
            try {
                // ✅ CORS preflight 요청 처리
                if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                    setCORSHeaders(exchange);
                    exchange.sendResponseHeaders(204, -1); // No Content
                    return;
                }

                // ✅ 실제 POST 요청 응답에도 CORS 허용 헤더 포함
                setCORSHeaders(exchange);

                // 기존 SubmitHandler 위임
                new SubmitHandler(hopper).handle(exchange);
            } catch (Exception e) {
                e.printStackTrace();
                String errorMsg = "Internal server error: " + e.getMessage();
                exchange.sendResponseHeaders(500, errorMsg.length());
                exchange.getResponseBody().write(errorMsg.getBytes());
                exchange.close();
            }
        });

        server.setExecutor(null); // 기본 executor 사용
        System.out.println("🚀 서버 실행 중: http://localhost:" + port);
        server.start();
    }

    // CORS 헤더 공통 설정 메서드
    private static void setCORSHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
    }
}
