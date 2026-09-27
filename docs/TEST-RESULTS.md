---
title: "Validação — Sprint 3 · SOA"
status: stable
created: 2026-09-27
last-updated: 2026-09-27
last-reviewed: 2026-09-27
---

# Validação — Sprint 3 · SOA

**FIAP · Arquitetura Orientada a Serviços e Web Services**

| Integrante | RM |
|---|---|
| João Marcelo Furtado Romero | RM555199 |
| Matheus Rivera Montovaneli | RM555499 |
| André Nakamatsu Rocha | RM555004 |

Execução verificada em **27/09/2026**: **85 testes aprovados, zero falhas e zero testes ignorados**. O build dos containers e o smoke de integração também passaram.

## Suítes e cobertura

| Serviço | Testes aprovados | Linhas | Branches |
|---|---:|---:|---:|
| Java / Customer Journey | 23 | 100.00% | 91.07% |
| Python / Vehicle Intelligence | 23 | 100.00% | 100.00% |
| .NET / Workshop Operations | 39 | 98.75% | 100.00% |

Os percentuais seguem o escopo configurado em cada ferramenta e não representam cobertura de todo o repositório. Java exclui adapters infra, bootstrap e Config; Python exclui `main.py`; .NET exclui Program, Infrastructure e Web. Os testes HTTP verificam comportamentos dessas camadas, mesmo quando elas ficam fora do cálculo de cobertura.

A suíte .NET inclui **20 cenários HTTP**, executando controllers, autenticação JWT e repository EF Core com SQLite em memória. O smoke exercita os serviços reais com PostgreSQL, MongoDB e Redis via gateway.

## Comportamentos verificados

- Java: login público, JWT inválido/expirado, 401/403, entradas inválidas, regras de jornada, 404/500/503 e declaração pública do login no OpenAPI.
- Python: endpoints públicos/protegidos, roles, JWT inválido/expirado, regras de recomendação, persistência/cache, 404/422/500 e schema de erros no OpenAPI.
- .NET HTTP: endpoints públicos; criação com 201/Location; leitura e transição persistidas; roles ADMIN/ADVISER/CUSTOMER/TECHNICIAN; token ausente, inválido, expirado e com assinatura incorreta; respostas 400/401/403/404/415/422/500; estado preservado após transição inválida; falha inesperada sem mensagem interna.
- Integração: autenticação, jornada, envio de dados, chamada Java → Python propagando JWT, consulta do cliente, ordem priorizada, transição e OpenAPI.
- O smoke validou **12 respostas de erro pelo gateway**: 401, 403, validação e 404 em cada API. Cada resposta possui os cinco campos de Problem Details e `application/problem+json`.
- Ruff, sintaxe Bash e configuração Compose aprovados.

## Evidências preservadas

- [Registro estruturado e hashes SHA-256](evidence/soa-validation.json): contagens extraídas dos relatórios JUnit/TRX, cobertura, comandos e snapshot dos arquivos de código, configuração e testes.
- [Saída real do smoke](evidence/smoke-test.txt): linhas PASS e resultado final `ALL SMOKE TESTS PASSED`. Os IDs e VINs registrados são dados sintéticos criados pelo script.

Esses arquivos preservam a evidência desta execução. Novas alterações no código exigem nova validação; os hashes permitem identificar divergência do snapshot verificado. Os relatórios completos são regeneráveis e permanecem fora do Git.

## Reproduzir

Com Docker Compose, Bash/curl/jq, JDK 17, uv/Python 3.12 ou superior e .NET SDK compatível com net8.0:

```bash
make test
make up
make smoke
```

`make up` reconstrói os serviços e reinicia o gateway para atualizar os endereços dos containers. O smoke cria dados de demonstração e exige sucesso de todas as verificações.

Relatórios completos:

- Java: `customer-journey-service/build/reports/tests/test/index.html` e `build/reports/jacoco/test/html/index.html` no mesmo serviço.
- Python: resumo pytest e `vehicle-intelligence-service/coverage.xml`.
- .NET: resultado dotnet test e `workshop-operations-service/tests/Ford.Workshop.Tests/coverage.cobertura.xml`. Use `--logger trx` para gerar um relatório de execução.

Fontes de autoridade: configurações de testes em build.gradle/pyproject.toml/csproj, suítes sob src/test ou tests e `scripts/smoke-test.sh`. Matriz da disciplina: [REQUIREMENTS.md](REQUIREMENTS.md).
