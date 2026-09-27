---
title: "Ford Zero Touch — Sprint 3 · SOA"
status: stable
created: 2026-09-26
last-updated: 2026-09-27
last-reviewed: 2026-09-27
---

# Ford Zero Touch — Sprint 3 · SOA

**FIAP · Arquitetura Orientada a Serviços e Web Services**

| Integrante | RM |
|---|---|
| João Marcelo Furtado Romero | RM555199 |
| Matheus Rivera Montovaneli | RM555499 |
| André Nakamatsu Rocha | RM555004 |

POC de pós-venda Ford: criar uma jornada, avaliar a condição do veículo por regras de negócio e controlar ordens de serviço. A entrega cobre arquitetura, REST nível 2, autenticação e autorização, JWT, testes automatizados, OpenAPI e tratamento de erros, conforme a seção SOA de `Ford_V2.pdf`.

## Serviços

| Serviço | Stack | Responsabilidade | Swagger |
|---|---|---|---|
| Customer Journey | Java 17 / Spring Boot | JWT, jornada e chamada autenticada à API de recomendações | http://localhost:8080/swagger-ui.html |
| Vehicle Intelligence | Python 3.12 / FastAPI | Avaliação do veículo e recomendação por regras | http://localhost:8000/docs |
| Workshop Operations | .NET 8 / ASP.NET Core | Prioridade e estados de ordens de serviço | http://localhost:5000/swagger |

Nginx expõe as APIs em `http://localhost:8088`, pelos prefixos `/journey`, `/intelligence` e `/workshop`. PostgreSQL, MongoDB e Redis atendem persistência e cache. As três stacks são uma escolha da POC; a disciplina não exige linguagens específicas.

## Executar

Requisitos: Docker Compose; Bash, curl e jq para o smoke test.

```bash
cp .env.example .env
make up
docker compose ps
./scripts/smoke-test.sh
```

Resultado esperado: `ALL SMOKE TESTS PASSED`. O script usa os perfis de veículo, consultor, técnico e cliente para concluir jornada e ordem, usando o risco retornado pela integração Java → Python. Confere persistência, atualização do cache, transições rejeitadas, erros e OpenAPI pelo gateway. Para parar preservando os dados: `docker compose down`.

## Autenticação

Obter JWT via `POST /journey/api/v1/auth/token` no gateway, com JSON `{"username":"admin@ford.com","password":"Ford@123"}`. Enviar `Authorization: Bearer <accessToken>` nas operações protegidas ou usar **Authorize** no Swagger. O token HS256 expira em uma hora; login, health e documentação são públicos.

Usuários de demonstração: `admin@ford.com` (ADMIN), `adviser@ford.com` (ADVISER), `customer@ford.com` (CUSTOMER), `technician@ford.com` (TECHNICIAN) e `vehicle@ford.com` (VEHICLE). Todos usam a senha local `Ford@123`. Credenciais e segredo de exemplo são locais à POC.

## Testes

Com JDK 17, uv/Python 3.12 ou superior e .NET SDK 8:

```bash
make test
make smoke
```

As suítes verificam regras de negócio, transições, entradas inválidas, autenticação, autorização e erros. A oficina inclui testes HTTP com JWT real e SQLite em memória. Java gera resultados em `customer-journey-service/build/reports/tests/test/index.html`; Python e .NET exibem resultados no terminal. READMEs dos serviços detalham cobertura e execução individual. `make verify` executa em sequência testes, build/health, E2E completo e resiliência, inclusive quando chamado com `make -j`.

`make resilience` testa a stack Docker local: para temporariamente o container Python, confere 503 e preservação da jornada, restaura o serviço e confirma a recuperação. O script também tenta restaurar Python e gateway ao sair por erro ou interrupção; detalhes em [Demonstração](docs/DEMO-RUNBOOK.md).

As três APIs retornam erros em Problem Details: `application/problem+json`, com `type`, `title`, `status`, `detail` e `instance`. O smoke confere esse contrato para 401, 403, validação e 404 nas três APIs. Resultados e evidências da execução estão em [Validação de SOA](docs/TEST-RESULTS.md).

## Documentação

- [Arquitetura e comunicação](docs/ARCHITECTURE.md)
- [Critérios da disciplina e evidências](docs/REQUIREMENTS.md)
- [Demonstração das APIs](docs/DEMO-RUNBOOK.md)
- [Decisões arquiteturais](docs/adr/)
- [Customer Journey](customer-journey-service/README.md)
- [Vehicle Intelligence](vehicle-intelligence-service/README.md)
- [Workshop Operations](workshop-operations-service/README.md)

A raiz integra os três serviços. Cada pasta de serviço também mantém um repositório Git independente.
