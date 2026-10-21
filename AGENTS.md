# AGENTS.md

Projeto: **Radar Corrida** — assistente leve para motorista de app (Android nativo, Kotlin, sem bibliotecas em runtime).

## Comandos (em `android-copilot/`)
- Testes: `gradlew testDebugUnitTest`
- APK: `gradlew assembleRelease`

## Regras
- Prioridade nº 1: avaliar a corrida rápido **sem pesar no celular**. Nada de OCR, captura de tela, rede ou IA no caminho do card.
- Não adicionar dependências de runtime sem necessidade comprovada.
- Toda mudança no parser (`Ride.kt`) precisa de teste em `RideTest.kt` com o texto real do card.
- PC de desenvolvimento tem 4 GB de RAM: manter `org.gradle.jvmargs` baixo.

## Mapa
- `android-copilot/README.md` — visão geral, recursos e limites.
- `android-copilot/vibe-coding-toolkit/` — submódulo de referência (não faz parte do app).
