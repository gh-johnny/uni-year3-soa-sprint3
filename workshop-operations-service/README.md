---
title: "Workshop Operations Service — SOA"
status: stable
created: 2026-09-26
last-updated: 2026-09-27
last-reviewed: 2026-09-27
---

# Workshop Operations Service — SOA

.NET 8/ASP.NET Core: cria/consulta ordens, calcula prioridade e controla estados. Domain, Application, Infrastructure e Web separam responsabilidades. State protege o fluxo; Strategy calcula prioridade; Repository isola EF Core/PostgreSQL.

## Executar

Execução integrada: `docker compose up --build -d` na raiz. Individualmente, disponibilize PostgreSQL e configure `ConnectionStrings__Postgres` e `Jwt__Secret` quando necessário:

```bash
dotnet restore Ford.Workshop.sln
dotnet run --project src/Ford.Workshop.Api
```

Swagger: http://localhost:5000/swagger. Health: `/health`. Recursos `/api/v1/work-orders` exigem JWT emitido por Customer Journey. ADVISER/ADMIN criam ordens; TECHNICIAN/ADMIN avançam estados; CUSTOMER também consulta. Erros de aplicação usam Problem Details.

## Testes

```bash
dotnet test Ford.Workshop.sln -c Release
```

Cenários de prioridade, transição, criação e consulta. Resultado no terminal; cobertura em `tests/Ford.Workshop.Tests/coverage.cobertura.xml`. Na raiz, `./scripts/smoke-test.sh` verifica autenticação, criação e transição pela API. Fontes: `src/`, `tests/` e `appsettings.json` no projeto API.
