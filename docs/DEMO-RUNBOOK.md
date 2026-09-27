---
title: "Demonstração — Sprint 3 · SOA"
status: stable
created: 2026-09-26
last-updated: 2026-09-27
last-reviewed: 2026-09-27
---

# Demonstração — Sprint 3 · SOA

## Preparação

Docker Compose ativo; portas 8088, 8080, 8000 e 5000 livres. Na raiz:

```bash
cp .env.example .env
docker compose config --quiet
docker compose up --build -d
docker compose ps
./scripts/smoke-test.sh
```

Resultado esperado: `ALL SMOKE TESTS PASSED`. O script cria dados e imprime IDs de jornada e ordem.

## Apresentação

1. Mostrar responsabilidades e comunicação em [ARCHITECTURE.md](ARCHITECTURE.md).
2. Abrir Swaggers: Java `http://localhost:8080/swagger-ui.html`, Python `http://localhost:8000/docs`, .NET `http://localhost:5000/swagger`.
3. Mostrar recurso protegido sem token: 401.
4. Obter JWT no login Java com `admin@ford.com` / `Ford@123`; usar **Authorize**.
5. Criar jornada: 201; solicitar recomendação: chamada Java → Python autenticada e resposta 200.
6. Criar ordem: 201; avançar Created → Diagnosing: 200; tentar salto inválido: 422.
7. Autenticar como `customer@ford.com` e tentar criar jornada: 403.
8. Executar `make test`; mostrar resultados e relatórios indicados nos READMEs.

As avaliações do veículo usam regras de negócio; as chamadas são demonstradas via REST/Swagger. Contratos de referência: OpenAPI nas três APIs e `scripts/smoke-test.sh`.

## Encerramento e diagnóstico

```bash
docker compose logs --tail=100 customer-journey vehicle-intelligence workshop-operations
docker compose down
```

`down` preserva dados nos volumes; `down --volumes` apaga dados locais.
