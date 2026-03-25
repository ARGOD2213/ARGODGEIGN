import http from "k6/http";
import { check, sleep } from "k6";

const baseUrl = __ENV.ARGUS_BASE_URL || "http://localhost:8080";
const facilityId = __ENV.ARGUS_FACILITY_ID || "FACTORY-HYD-001";

const sensorProfiles = [
  { deviceId: "COMP-01", sensorType: "VIBRATION", unit: "mm/s", base: 4.1, spread: 3.2, location: "SYNTHESIS" },
  { deviceId: "PUMP-01", sensorType: "BEARING_TEMPERATURE", unit: "C", base: 78.0, spread: 18.0, location: "UTILITY" },
  { deviceId: "BOILER-01", sensorType: "TEMPERATURE", unit: "C", base: 122.0, spread: 24.0, location: "BOILER" },
  { deviceId: "IA-01", sensorType: "INSTRUMENT_AIR_PRESSURE", unit: "bar", base: 5.9, spread: 1.4, location: "IA_SYSTEM" },
  { deviceId: "GAS-01", sensorType: "GAS_LEAK", unit: "ppm", base: 8.0, spread: 24.0, location: "SYNTHESIS" }
];

export const options = {
  stages: [
    { duration: "1m", target: 10 },
    { duration: "3m", target: 40 },
    { duration: "2m", target: 5 }
  ],
  thresholds: {
    http_req_failed: ["rate<0.01"],
    http_req_duration: ["p(95)<1200"]
  }
};

function buildPayload() {
  const profile = sensorProfiles[Math.floor(Math.random() * sensorProfiles.length)];
  const value = Math.round((profile.base + Math.random() * profile.spread) * 100) / 100;
  return JSON.stringify({
    deviceId: profile.deviceId,
    sensorType: profile.sensorType,
    value,
    unit: profile.unit,
    location: profile.location,
    facilityId
  });
}

export default function () {
  const response = http.post(
    `${baseUrl}/api/v1/sensor/ingest`,
    buildPayload(),
    { headers: { "Content-Type": "application/json" } }
  );

  check(response, {
    "ingest accepted": (r) => r.status === 201 || r.status === 200
  });

  sleep(0.2);
}
