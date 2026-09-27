#!/usr/bin/env bash
set -euo pipefail

gateway="${GATEWAY_URL:-http://localhost:8088}"
problem_file="$(mktemp)"
trap 'rm -f "$problem_file"' EXIT

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

check_problem() {
  local expected="$1" method="$2" path="$3" token="${4:-}" payload="${5:-}"
  local args=(--silent --show-error --request "$method" --output "$problem_file"
    --write-out '%{http_code} %{content_type}')
  if test -n "$token"; then args+=(--header "Authorization: Bearer $token"); fi
  if test -n "$payload"; then args+=(--header 'Content-Type: application/json' --data "$payload"); fi
  local metadata content_type
  metadata="$(curl "${args[@]}" "$gateway$path")"
  content_type="${metadata#* }"
  test "${metadata%% *}" = "$expected"
  test "${content_type%%;*}" = "application/problem+json"
  jq -e --argjson expected "$expected" '
    (keys | sort) == ["detail", "instance", "status", "title", "type"]
    and .status == $expected
    and .type == ("https://ford.example/problems/" + ($expected | tostring))
    and (.title | type == "string" and length > 0)
    and (.detail | type == "string" and length > 0)
    and (.instance | startswith("/api/v1/"))
  ' "$problem_file" >/dev/null
  echo "PASS Problem Details $expected $method $path"
}

wait_for journey "$gateway/journey/actuator/health"
wait_for intelligence "$gateway/intelligence/health"
wait_for workshop "$gateway/workshop/health"

admin_token="$(login admin@ford.com)"
vehicle_token="$(login vehicle@ford.com)"
customer_token="$(login customer@ford.com)"
echo "PASS authentication profiles"

check_problem 401 GET /journey/api/v1/journeys/00000000-0000-0000-0000-000000000000
check_problem 401 GET /intelligence/api/v1/vehicles/1FMCU9GDXMUA99999/risk
check_problem 401 GET /workshop/api/v1/work-orders/00000000-0000-0000-0000-000000000000

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

check_problem 403 POST /journey/api/v1/journeys "$customer_token" \
  "{\"customerId\":\"$customer_id\",\"vin\":\"$vin\"}"
check_problem 403 POST /intelligence/api/v1/telemetry "$customer_token" "$telemetry"
check_problem 403 POST /workshop/api/v1/work-orders "$customer_token" \
  "{\"appointmentId\":\"$appointment_id\",\"vin\":\"$vin\",\"description\":\"Inspection\",\"riskLevel\":\"HIGH\",\"vehicleImmobilized\":false}"
check_problem 400 POST /journey/api/v1/journeys "$admin_token" '{}'
check_problem 422 POST /intelligence/api/v1/telemetry "$admin_token" '{"vin":"invalid"}'
check_problem 400 POST /workshop/api/v1/work-orders "$admin_token" '{}'
check_problem 404 GET /journey/api/v1/journeys/00000000-0000-0000-0000-000000000000 "$admin_token"
missing_vin="1FN$(date +%s%N | tail -c 15)"
check_problem 404 GET "/intelligence/api/v1/vehicles/$missing_vin/risk" "$admin_token"
check_problem 404 GET /workshop/api/v1/work-orders/00000000-0000-0000-0000-000000000000 "$admin_token"

curl --silent --fail http://localhost:8080/v3/api-docs | jq -e '
  .paths["/api/v1/auth/token"].post.security == [] and .security[0].bearerAuth == []
' >/dev/null
curl --silent --fail http://localhost:8000/openapi.json >/dev/null
curl --silent --fail http://localhost:5000/swagger/v1/swagger.json >/dev/null
echo "PASS OpenAPI for all services"
echo "ALL SMOKE TESTS PASSED"
