---
title: "Vehicle Intelligence Service — SOA"
status: stable
created: 2026-09-26
last-updated: 2026-09-27
last-reviewed: 2026-09-27
---

# Vehicle Intelligence Service — SOA

Python 3.12/FastAPI: recebe dados do veículo, aplica regras de condição e retorna risco, motivos e próxima ação. O pipeline separa extração de sinais e avaliação; protocolos isolam MongoDB e cache Redis da aplicação.

## Executar

Execução integrada: `docker compose up --build -d` na raiz. Individualmente, disponibilize MongoDB e Redis e configure `MONGO_URL`, `REDIS_URL` e `JWT_SECRET` quando necessário:

```bash
uv sync --all-groups
uv run uvicorn ford_intelligence.main:app --app-dir src --reload
```

Swagger: http://localhost:8000/docs. Health: `/health`. Operações `/api/v1/telemetry`, `/api/v1/recommendations/evaluate` e `/api/v1/vehicles/{vin}/risk` exigem JWT e roles. Customer Journey emite o token compartilhado. Erros usam `application/problem+json`, com `type`, `title`, `status`, `detail` e `instance`, incluindo 401/403 e validação. Falhas inesperadas não expõem a mensagem interna da exceção.

## Testes

```bash
uv run ruff check .
uv run pytest
```

Cenários de avaliação, persistência/cache, validação, autenticação, roles e erros. Resultado no terminal e `coverage.xml`. Na raiz, `./scripts/smoke-test.sh` verifica a chamada autenticada de Customer Journey. Fontes: `src/`, `tests/` e `pyproject.toml`.
