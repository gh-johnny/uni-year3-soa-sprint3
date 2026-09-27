.PHONY: up down build test smoke verify logs clean

up:
	docker compose up --build -d
	docker compose restart gateway

down:
	docker compose down

build:
	docker compose build

test:
	cd customer-journey-service && ./gradlew test jacocoTestReport
	cd vehicle-intelligence-service && uv sync --all-groups && uv run ruff check . && uv run pytest
	dotnet test workshop-operations-service/Ford.Workshop.sln --configuration Release

smoke:
	./scripts/smoke-test.sh

verify: test up smoke

logs:
	docker compose logs -f --tail=200

clean:
	docker compose down --volumes --remove-orphans
