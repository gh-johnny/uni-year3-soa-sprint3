---
title: "ADR 001 — Bounded contexts poliglotas"
status: stable
created: 2026-09-26
last-updated: 2026-09-27
last-reviewed: 2026-09-27
---

# ADR 001 — Bounded contexts poliglotas

**Status:** aceito · **Data:** 2026-09-26

## Contexto

A entrega de SOA pede separação de responsabilidades e comunicação autenticada entre serviços. Jornada, avaliação do veículo e oficina possuem modelos distintos.

## Decisão

Manter Java/Spring para jornada e autenticação, Python/FastAPI para recomendações por regras de negócio e .NET para operações de oficina. Contratos REST/JSON v1 e JWT comum integram os contextos.

## Consequências

Os serviços demonstram isolamento de responsabilidades. As três stacks são uma escolha da solução, não uma exigência da disciplina. Compose, gateway e documentação central organizam a execução. Não compartilhar database nem classes entre serviços.
