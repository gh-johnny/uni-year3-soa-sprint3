---
title: "Rastreabilidade — Sprint 3 · SOA"
status: stable
created: 2026-09-26
last-updated: 2026-09-27
last-reviewed: 2026-09-27
---

# Rastreabilidade — Sprint 3 · SOA

**Equipe:** João Marcelo Furtado Romero — RM555199; Matheus Rivera Montovaneli — RM555499; André Nakamatsu Rocha — RM555004.

Fonte: seção **SPRINT 3 — ARQUITETURA ORIENTADA A SERVICOS E WEB SERVICES** de `Ford_V2.pdf`. Entrega: 27/09/2026. Esta matriz cobre somente SOA.

| Critério | Peso | Evidências |
|---|---:|---|
| Arquitetura da solução | 20% | `ARCHITECTURE.md`: componentes, responsabilidades, comunicação e autenticação; três serviços com camadas separadas |
| Maturidade REST nível 2 | 20% | Recursos `/journeys`, `/vehicles/{vin}/risk` e `/work-orders`; GET/POST; respostas 200, 201, 202, 400, 401, 403, 404 e 422 conforme operação |
| Autenticação e autorização | 20% | Login, health e Swagger públicos; recursos protegidos; roles ADMIN, ADVISER, CUSTOMER, VEHICLE, TECHNICIAN e SERVICE |
| JWT | 15% | Emissão Java e validação nos serviços; HS256; claims de identidade, roles e expiração de uma hora |
| Testes automatizados | 15% | Suítes Java, Python e .NET; sucesso, erro e acesso não autorizado; smoke; comandos e relatórios nos READMEs |
| Documentação e tratamento de erros | 10% | OpenAPI/Swagger nas APIs; handlers de erros por serviço; README de execução |

## Verificação

```bash
make test
docker compose up --build -d
make smoke
```

Os resultados das suítes e do smoke fornecem evidência da execução. Fontes: controllers/routers, configurações JWT, testes e `scripts/smoke-test.sh` nos três serviços.
