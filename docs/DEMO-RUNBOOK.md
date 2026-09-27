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
make up
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
6. Criar ordem com o risco retornado: 201; repetir o agendamento: 409; tentar salto inválido: 422. Com `technician@ford.com`, avançar Created → Diagnosing → AwaitingApproval → InProgress → QualityCheck → Completed.
7. Autenticar como `customer@ford.com` e tentar criar jornada: 403.
8. Executar `make test` e `make smoke`; mostrar resultados e relatórios indicados nos READMEs. O smoke também conclui a jornada em COMPLETED, valida cache e consulta os estados persistidos após cada transição.

As avaliações do veículo usam regras de negócio; as chamadas são demonstradas via REST/Swagger. Contratos de referência: OpenAPI nas três APIs e `scripts/smoke-test.sh`.

## Falha e recuperação

Com a stack local saudável, executar `make resilience`. O script usa `http://localhost:8088` e o Compose desta pasta, cria uma jornada sintética, para somente `vehicle-intelligence` e verifica cinco respostas 503 com Problem Details. Remove apenas o cache dessa jornada e relê o estado persistido, que deve continuar DETECTED; a oficina deve permanecer disponível.

Depois, inicia Python, aguarda health, reinicia o gateway e aguarda sua readiness. Dentro de uma janela de 45 segundos, a recomendação deve voltar a responder 200. Duas chamadas bem-sucedidas e consultas posteriores conferem a recuperação, o risco persistido e a jornada RECOMMENDED com cache atualizado.

Resultado esperado: `ALL RESILIENCE TESTS PASSED`. O cleanup tenta restaurar Python e gateway se houver erro, Ctrl+C ou SIGTERM durante a parada. Uma interrupção SIGTERM nessa fase também foi verificada; evidência em [Validação](TEST-RESULTS.md). Em falha de Docker durante a restauração, executar `docker compose start vehicle-intelligence` e `docker compose restart gateway`, depois conferir health. `make verify` encadeia testes, build, E2E e resiliência em ordem.

## Encerramento e diagnóstico

```bash
docker compose logs --tail=100 customer-journey vehicle-intelligence workshop-operations
docker compose down
```

`down` preserva dados nos volumes; `down --volumes` apaga dados locais.
