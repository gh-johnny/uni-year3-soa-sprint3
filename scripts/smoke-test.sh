#!/usr/bin/env bash
set -euo pipefail

gateway="${GATEWAY_URL:-http://localhost:8088}"

wait_for() {
  local name="$1" url="$2"
  for attempt in $(seq 1 30); do
    if curl --silent --fail --max-time 3 "$url" >/dev/null; then
      echo "PASS health $name"
      return
    fi
    sleep 2
  done
  echo "FAIL health $name" >&2
  exit 1
}

login() {
  local username="$1"
  curl --silent --fail --request POST "$gateway/journey/api/v1/auth/token" \
    --header 'Content-Type: application/json' \
    --data "{\"username\":\"$username\",\"password\":\"Ford@123\"}" | jq -er '.accessToken'
}

wait_for journey "$gateway/journey/actuator/health"
wait_for intelligence "$gateway/intelligence/health"
wait_for workshop "$gateway/workshop/health"

admin_token="$(login admin@ford.com)"
vehicle_token="$(login vehicle@ford.com)"
customer_token="$(login customer@ford.com)"
echo "PASS authentication profiles"

unauthorized_status="$(curl --silent --output /dev/null --write-out '%{http_code}' "$gateway/journey/api/v1/journeys/00000000-0000-0000-0000-000000000000")"
test "$unauthorized_status" = "401"
echo "PASS protected endpoint returns 401"

vin="1FM$(date +%s%N | tail -c 15)"
customer_id="$(tr -d '\n' </proc/sys/kernel/random/uuid)"
journey_json="$(curl --silent --fail --request POST "$gateway/journey/api/v1/journeys" \
  --header "Authorization: Bearer $admin_token" \
  --header 'Content-Type: application/json' \
  --data "{\"customerId\":\"$customer_id\",\"vin\":\"$vin\"}")"
journey_id="$(jq -er '.id' <<<"$journey_json")"
jq -e '.status == "DETECTED"' <<<"$journey_json" >/dev/null
echo "PASS create journey $journey_id"

telemetry="{\"vin\":\"$vin\",\"odometer_km\":42420,\"oil_life_percent\":12,\"battery_voltage\":11.0,\"engine_temperature_c\":120,\"diagnostic_codes\":[\"P0217\",\"P0562\"]}"
intelligence_json="$(curl --silent --fail --request POST "$gateway/intelligence/api/v1/telemetry" \
  --header "Authorization: Bearer $vehicle_token" --header 'Content-Type: application/json' --data "$telemetry")"
jq -e '.risk_level == "HIGH" or .risk_level == "CRITICAL"' <<<"$intelligence_json" >/dev/null
echo "PASS ingest telemetry and classify risk"

recommendation_payload="$(jq '{
  vin,
  odometerKm: .odometer_km,
  oilLifePercent: .oil_life_percent,
  batteryVoltage: .battery_voltage,
  engineTemperatureC: .engine_temperature_c,
  diagnosticCodes: .diagnostic_codes
}' <<<"$telemetry")"
recommendation_json="$(curl --silent --fail --request POST "$gateway/journey/api/v1/journeys/$journey_id/recommendation" \
  --header "Authorization: Bearer $admin_token" --header 'Content-Type: application/json' --data "$recommendation_payload")"
jq -e '.action != null and .riskLevel != null' <<<"$recommendation_json" >/dev/null
echo "PASS cross-service recommendation with JWT"

customer_view="$(curl --silent --fail "$gateway/journey/api/v1/journeys/$journey_id" --header "Authorization: Bearer $customer_token")"
jq -e '.status == "RECOMMENDED"' <<<"$customer_view" >/dev/null
echo "PASS customer reads recommended journey"

appointment_id="$(tr -d '\n' </proc/sys/kernel/random/uuid)"
work_order_json="$(curl --silent --fail --request POST "$gateway/workshop/api/v1/work-orders" \
  --header "Authorization: Bearer $admin_token" --header 'Content-Type: application/json' \
  --data "{\"appointmentId\":\"$appointment_id\",\"vin\":\"$vin\",\"description\":\"Predictive inspection\",\"riskLevel\":\"HIGH\",\"vehicleImmobilized\":false}")"
work_order_id="$(jq -er '.id' <<<"$work_order_json")"
jq -e '.priority == "Urgent" and .status == "Created"' <<<"$work_order_json" >/dev/null
echo "PASS create prioritized work order $work_order_id"

transition_json="$(curl --silent --fail --request POST "$gateway/workshop/api/v1/work-orders/$work_order_id/transitions" \
  --header "Authorization: Bearer $admin_token" --header 'Content-Type: application/json' \
  --data '{"target":"Diagnosing"}')"
jq -e '.status == "Diagnosing"' <<<"$transition_json" >/dev/null
echo "PASS transition work order"

curl --silent --fail http://localhost:8080/v3/api-docs >/dev/null
curl --silent --fail http://localhost:8000/openapi.json >/dev/null
curl --silent --fail http://localhost:5000/swagger/v1/swagger.json >/dev/null
echo "PASS OpenAPI for all services"
echo "ALL SMOKE TESTS PASSED"
