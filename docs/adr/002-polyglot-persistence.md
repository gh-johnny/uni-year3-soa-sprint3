---
title: "ADR 002 — Persistência poliglota e cache"
status: stable
created: 2026-09-26
last-updated: 2026-09-27
last-reviewed: 2026-09-27
---

# ADR 002 — Persistência poliglota e cache

**Status:** aceito · **Data:** 2026-09-26

## Decisão

PostgreSQL por database guarda aggregates transacionais; MongoDB guarda telemetria evolutiva; Redis aplica cache-aside de curta duração. Cada serviço é dono do próprio schema.

## Consequências

Transações permanecem locais e consistentes; telemetria muda sem migração rígida; leituras recentes ficam rápidas. Não há transação distribuída. Eventos/outbox são a evolução quando consistência eventual for exigida.

