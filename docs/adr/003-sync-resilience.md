---
title: "ADR 003 — REST síncrono com resiliência na POC"
status: stable
created: 2026-09-26
last-updated: 2026-09-27
last-reviewed: 2026-09-27
---

# ADR 003 — REST síncrono com resiliência na POC

**Status:** aceito · **Data:** 2026-09-26

## Contexto

A demonstração precisa ser simples de executar e evidenciar REST nível 2, autenticação e falha controlada.

## Decisão

Usar REST síncrono entre jornada e inteligência, com timeout, retry limitado, circuit breaker e erro 503 padronizado. Propagar o JWT para manter autorização de ponta a ponta.

## Consequências

Fluxo e debugging são claros, porém existe acoplamento temporal. Retry só deve envolver operação idempotente de avaliação. Para escala/produção, comandos e eventos de longa duração migram para broker + Outbox.

