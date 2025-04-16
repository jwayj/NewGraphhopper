let map;
let selectedEdgeIds = []; // ✅ 선택된 edge 저장용

document.addEventListener("DOMContentLoaded", () => {
  map = L.map("map").setView([37.5665, 126.9780], 13);

  L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
    attribution: '© OpenStreetMap contributors'
  }).addTo(map);

  document.getElementById("start-search-btn").addEventListener("click", () => {
    execDaumPostcode("start-address", "start-map");
  });

  document.getElementById("end-search-btn").addEventListener("click", () => {
    execDaumPostcode("end-address", "end-map");
  });

  // ✅ 사용자 피드백 전송 버튼 이벤트 등록
  document.getElementById("feedbackBtn").addEventListener("click", () => {
    if (selectedEdgeIds.length === 0) {
      alert("⚠️ 선택된 경로가 없습니다.");
      return;
    }

    const payload = [
      {
        selectedEdges: selectedEdgeIds.map(edge => ({ edge }))
      }
    ];

    fetch("http://localhost:8080/feedback", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    })
      .then(response => {
        if (!response.ok) throw new Error("서버 오류");
        alert("✅ 피드백이 전송되었습니다!");
        selectedEdgeIds = []; // 전송 후 초기화
      })
      .catch(err => {
        console.error("❌ 피드백 전송 실패:", err);
        alert("❌ 피드백 전송 실패");
      });
  });
});

// ✅ 서버에 경로 요청
function sendData() {
  const startAddress = document.getElementById("start-address").value;
  const endAddress = document.getElementById("end-address").value;
  const distance = document.getElementById("distance").value;
  const slope = document.querySelector('input[name="slope"]:checked')?.value || 'medium';

  fetch('http://localhost:4567/submit', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ startAddress, endAddress, distance, slope })
  })
  .then(response => {
    if (!response.ok) throw new Error("서버 오류");
    alert("✅ 데이터가 성공적으로 전송되었습니다!");

    loadGeoJsonToMap("route1.geojson", "blue");
    loadGeoJsonToMap("route.geojson", "green");
  })
  .catch(error => {
    console.error("전송 오류:", error);
    alert("❌ 서버와 연결 중 오류가 발생했습니다.");
  });
}

window.sendData = sendData; // ✅ 전역 등록 (버튼 onclick 사용 시 필수)

// ✅ GeoJSON 불러오고 클릭 가능하게
function loadGeoJsonToMap(url, color) {
  fetch(url)
      .then(response => {
          if (!response.ok) {
              throw new Error('GeoJSON 로딩 실패');
          }
          return response.json();  // 응답을 JSON으로 파싱
      })
      .then(data => {
          L.geoJSON(data, {
              style: function (feature) {
                  return {
                      color: color,
                      weight: 5,
                      opacity: 0.7
                  };
              }
          }).addTo(map);
      })
      .catch(err => {
          console.error("❌ GeoJSON 로딩 실패:", err);
          alert("❌ GeoJSON 로딩 실패");
      });
}

