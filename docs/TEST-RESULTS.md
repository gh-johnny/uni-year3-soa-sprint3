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

Execução verificada em **27/09/2026**: **114 testes aprovados, zero falhas e zero testes ignorados**. Build/health, E2E completo, falha/recuperação com Docker e restauração após SIGTERM também passaram.

## Suítes e cobertura

| Serviço | Testes aprovados | Linhas | Branches |
|---|---:|---:|---:|
| Java / Customer Journey | 39 | 100.00% | 91.07% |
| Python / Vehicle Intelligence | 31 | 100.00% | 100.00% |
| .NET / Workshop Operations | 44 | 98.79% | 100.00% |

Os percentuais seguem o escopo configurado em cada ferramenta e não representam cobertura de todo o repositório. Java exclui adapters infra, bootstrap e Config; Python exclui `main.py`; .NET exclui Program, Infrastructure e Web. Os testes HTTP verificam comportamentos dessas camadas, mesmo quando elas ficam fora do cálculo de cobertura.

A suíte .NET inclui **21 cenários HTTP**, executando controllers, autenticação JWT e repository EF Core com SQLite em memória, mais testes do conflito de índice PostgreSQL com interceptor. Java inclui dois testes do adapter real contra um servidor HTTP local controlado. O smoke e o teste de resiliência exercitam os serviços reais com PostgreSQL, MongoDB e Redis via gateway.

## Comportamentos verificados

- Java: login público, JWT inválido/expirado e issuer/audience incorretos ou ausentes; 401/403, limites de telemetria com 400 antes da integração, regras de jornada, 404/500/503 e login público no OpenAPI.
- Python: endpoints públicos/protegidos, roles, JWT inválido/expirado, regras de recomendação, persistência/cache, VIN null/número/lista/objeto com 422 nos dois endpoints de entrada, 404/422/500 e schema de erros no OpenAPI.
- .NET HTTP: endpoints públicos; criação com 201/Location; leitura e transição persistidas; roles ADMIN/ADVISER/CUSTOMER/TECHNICIAN; token ausente, inválido, expirado e com assinatura incorreta; respostas 400/401/403/404/415/422/500; estado preservado após transição inválida; falha inesperada sem mensagem interna.
- Oficina: criação repetida para o mesmo agendamento retorna 409 sem alterar a ordem original; aplicação não escreve ao detectar duplicação; o adapter converte somente a violação do índice de agendamento, preservando outros erros de banco.
- E2E: VEHICLE envia dados, ADVISER cria jornada/solicita recomendação/cria ordem com o risco retornado, TECHNICIAN avança oficina e CUSTOMER consulta. Ordem termina Completed e jornada COMPLETED. Leituras verificam IDs, VIN, agendamento, prioridade, estados e cache; erros não alteram os recursos.
- O smoke validou **18 respostas de erro pelo gateway**, incluindo 409, transições rejeitadas e regressões de validação. Cada resposta possui os cinco campos de Problem Details e `application/problem+json`.
- Adapter real: contrato HTTP/Bearer, três tentativas por falha antes da abertura, circuito OPEN sem novas chamadas à dependência e HALF_OPEN → CLOSED após duas sondagens. Usa espera/janela reduzidas em teste; produção mantém os parâmetros de application.yml.
- Resiliência Docker: Python parado, cinco respostas 503, jornada persistida inalterada após remover apenas seu cache, oficina disponível, Python/gateway restaurados e recomendação 200 com risco persistido e cache atualizado.
- Interrupção controlada: SIGTERM enviado após o container Python parar; script encerrou com 143 e o cleanup restaurou Python/gateway. Health das três APIs conferido depois da restauração.
- Ruff, sintaxe Bash e configuração Compose aprovados.

## Evidências preservadas

- [Registro estruturado e hashes SHA-256](evidence/soa-validation.json): contagens extraídas dos relatórios JUnit/TRX, cobertura, comandos e snapshot dos arquivos de código, configuração e testes.
- [Saída real do smoke](evidence/smoke-test.txt): linhas PASS e resultado final `ALL SMOKE TESTS PASSED`. Os IDs e VINs registrados são dados sintéticos criados pelo script.
- [Saída real da falha/recuperação](evidence/resilience-test.txt): parada/restauração do container, verificações e `ALL RESILIENCE TESTS PASSED`.
- [Restauração após SIGTERM](evidence/resilience-cleanup.txt): interrupção durante a parada e health aprovado após cleanup.

Esses arquivos preservam a evidência desta execução. Novas alterações no código exigem nova validação; os hashes permitem identificar divergência do snapshot verificado. Os relatórios completos são regeneráveis e permanecem fora do Git.

## Reproduzir

Com Docker Compose, Bash/curl/jq, JDK 17, uv/Python 3.12 ou superior e .NET SDK compatível com net8.0:

```bash
make test
make up
make smoke
make resilience
```

`make up` reconstrói os serviços e reinicia o gateway para atualizar os endereços dos containers. O smoke cria dados de demonstração e exige sucesso de todas as verificações. `make resilience` para e restaura temporariamente Python na stack local; detalhes e recuperação manual em [Demonstração](DEMO-RUNBOOK.md). `make verify` executa as quatro etapas em sequência.

Relatórios completos:

- Java: `customer-journey-service/build/reports/tests/test/index.html` e `build/reports/jacoco/test/html/index.html` no mesmo serviço.
- Python: resumo pytest e `vehicle-intelligence-service/coverage.xml`.
- .NET: resultado dotnet test e `workshop-operations-service/tests/Ford.Workshop.Tests/coverage.cobertura.xml`. Use `--logger trx` para gerar um relatório de execução.

Fontes de autoridade: configurações de testes em build.gradle/pyproject.toml/csproj, suítes sob src/test ou tests, `scripts/smoke-test.sh` e `scripts/resilience-test.sh`. Matriz da disciplina: [REQUIREMENTS.md](REQUIREMENTS.md).
