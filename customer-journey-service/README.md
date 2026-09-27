---
title: "Customer Journey Service — SOA"
status: stable
created: 2026-09-26
last-updated: 2026-09-27
last-reviewed: 2026-09-27
---

# Customer Journey Service — SOA

Java 17/Spring Boot: emissão JWT, roles e ciclo de vida de jornadas. Domínio/aplicação são separados dos adapters JPA/REST. PostgreSQL guarda jornadas; Redis fornece cache; o adapter chama Vehicle Intelligence propagando JWT, com timeout, retry e circuit breaker.

## Executar

Execução integrada: `docker compose up --build -d` na raiz. Individualmente, disponibilize PostgreSQL e Redis e configure `DB_URL`, `DB_USER`, `DB_PASSWORD`, `REDIS_HOST`, `INTELLIGENCE_URL` e `JWT_SECRET` quando necessário:

```bash
./gradlew bootRun
```

Swagger: http://localhost:8080/swagger-ui.html. Health: `/actuator/health`. Login público: `POST /api/v1/auth/token`. Usuários: `admin@ford.com`, `adviser@ford.com`, `customer@ford.com`, `vehicle@ford.com`; senha local `Ford@123`. Recursos `/api/v1/journeys` exigem JWT e roles. Erros de aplicação usam Problem Details.

## Testes

```bash
./gradlew test jacocoTestReport
```

Cenários de domínio, aplicação, API e segurança. Resultado em `build/reports/tests/test/index.html`; cobertura em `build/reports/jacoco/test/html/index.html`. Na raiz, `./scripts/smoke-test.sh` verifica integração real. Fontes: `src/`, `build.gradle` e `src/main/resources/application.yml`.
