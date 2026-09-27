def headers(token, *roles):
    return {"Authorization": f"Bearer {token(*roles)}"}


def test_health_is_public(client):
    assert client.get("/health").json() == {"status": "UP"}


def test_telemetry_requires_authentication(client, telemetry):
    response = client.post("/api/v1/telemetry", json=telemetry)
    assert response.status_code == 401


def test_invalid_and_expired_tokens_are_rejected(client, telemetry, token):
    invalid = client.post(
        "/api/v1/telemetry", json=telemetry, headers={"Authorization": "Bearer nope"}
    )
    expired = client.post(
        "/api/v1/telemetry",
        json=telemetry,
        headers=headers(token, "VEHICLE")
        | {"Authorization": f"Bearer {token('VEHICLE', expired=True)}"},
    )
    assert invalid.status_code == expired.status_code == 401


def test_role_is_enforced(client, telemetry, token):
    response = client.post("/api/v1/telemetry", json=telemetry, headers=headers(token, "CUSTOMER"))
    assert response.status_code == 403


def test_ingest_and_read_latest_risk(client, telemetry, token):
    created = client.post("/api/v1/telemetry", json=telemetry, headers=headers(token, "VEHICLE"))
    found = client.get(
        f"/api/v1/vehicles/{telemetry['vin']}/risk", headers=headers(token, "CUSTOMER")
    )
    assert created.status_code == 202
    assert found.status_code == 200
    assert found.json()["risk_level"] in {"MEDIUM", "HIGH", "CRITICAL"}


def test_evaluate_endpoint_and_validation(client, telemetry, token):
    valid = client.post(
        "/api/v1/recommendations/evaluate", json=telemetry, headers=headers(token, "SERVICE")
    )
    invalid = client.post(
        "/api/v1/recommendations/evaluate",
        json=telemetry | {"vin": "invalid"},
        headers=headers(token, "ADMIN"),
    )
    assert valid.status_code == 200
    assert invalid.status_code == 422


def test_missing_risk_returns_404(client, token):
    response = client.get(
        "/api/v1/vehicles/1FMCU9GDXMUA99999/risk", headers=headers(token, "ADVISER")
    )
    assert response.status_code == 404


def test_unhandled_errors_are_standardized(client, telemetry, token, service):
    async def fail(_):
        raise RuntimeError("database unavailable")

    service.evaluate = fail
    response = client.post("/api/v1/telemetry", json=telemetry, headers=headers(token, "VEHICLE"))
    assert response.status_code == 500
    assert response.json()["type"] == "internal_error"
