# GUIA DE PROMPTS — RADAR CORRIDA

Este arquivo contém os **3 Prompts Práticos** para você copiar e colar a qualquer momento, dependendo do que deseja fazer no projeto:

---

## 🟢 PROMPT 1: Evolução Contínua e Otimização do App (Android Ultra-Leve)
> **Quando usar:** Quando quiser pedir novas funções no aplicativo Android, melhorar o layout do selo ou refinar o semáforo.

```markdown
Atue como Engenheiro Sênior Android e Arquiteto de Baixo Consumo do projeto "Radar Corrida".

Contexto do Projeto:
- Repositório: https://github.com/gustavobaptistadev-ops/radar-corrida
- Diretório: android-copilot/
- Stack: Android Nativo, Kotlin puro, minSdk 26, sem bibliotecas pesadas de terceiros (sem Jetpack Compose, sem AppCompat desnecessário).
- Compilação: Automatizada via GitHub Actions (.github/workflows/android.yml), gerando APKs minificados (< 50 KB).

Leis Invioláveis de Performance:
1. ZERO OCR, ZERO CAPTURA DE TELA e ZERO IA LOCAL no caminho da detecção da corrida. O celular fica no painel sob sol e NÃO PODE AQUECER nem drenar bateria.
2. Interceptação unicamente via `AccessibilityService` (`AccessibilityNodeInfo`), lendo textos já presentes na memória da UI do sistema.
3. Overlay com `WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY` com flags `FLAG_NOT_FOCUSABLE` e `FLAG_NOT_TOUCHABLE` (o motorista NUNCA pode ter o clique bloqueado no botão "Aceitar").
4. Todo novo recurso ou alteração no parser (`Ride.kt`) DEVE ser validado com testes unitários em `RideTest.kt`.

Tarefa a ser executada:
[Descreva aqui o que deseja implementar, ex: "Adicionar configuração para o motorista definir se quer aviso sonoro apenas em corridas vermelhas" ou "Refinar o cálculo para descontar pedágio"]
```

---

## 🟡 PROMPT 2: Calibração de Novos Cards da Uber / 99 / inDrive
> **Quando usar:** Quando a Uber ou a 99 atualizarem o visual do aplicativo e o Radar deixar de ler algum card específico.

```markdown
Atue no parser de texto do projeto "Radar Corrida" (arquivo: android-copilot/app/src/main/java/com/motorista/radar/Ride.kt).

Um novo modelo de card apareceu na tela do aplicativo de motorista e precisamos calibrar o `RideParser.kt`.

Texto real extraído do novo card:
"""
[Cole aqui exatamente o texto que apareceu na tela da Uber/99 ou o print transcrito]
"""

O que deve ser extraído:
- Valor da corrida (R$)
- Distância até o passageiro (km ou m)
- Distância da viagem (km)
- Tempo total estimado (minutos)
- (Opcional) Nota do passageiro ou dinâmica

Instruções:
1. Adicione um novo teste unitário em `android-copilot/app/src/test/java/com/motorista/radar/RideTest.kt` contendo essa string real.
2. Ajuste as expressões regulares em `RideParser.kt` sem quebrar os testes anteriores.
3. Mantenha o processamento em microssegundos, sem loops lentos ou alocações desnecessárias.
```

---

## 🔵 PROMPT 3: Construção do Assessor Financeiro no WhatsApp (Módulo Externo)
> **Quando usar:** Quando formos criar o bot de WhatsApp com IA que recebe áudio de abastecimento e faz o fechamento do dia.

```markdown
Atue como Engenheiro Backend e Especialista em IA para o projeto "Radar Corrida".

Objetivo: Construir o Módulo 2 do projeto: o "Assessor Financeiro do Motorista via WhatsApp".

Premissa Arquitetural:
- Todo o processamento pesado de IA e áudio roda OFF-DEVICE (fora do celular, em servidor/cloud leve) para manter o celular do motorista 100% frio e sem consumo de bateria.
- Integração via WhatsApp (Evolution API, Baileys ou Z-API).
- Uso da API do Gemini para processamento de linguagem natural (áudio e texto).

Funcionalidades:
1. Recepção de áudio/texto no posto:
   - Exemplo: "Botei 50 no etanol a 3,89, odômetro 145.200".
   - A IA extrai os litros calculados (~12,85 L), registra o odômetro e atualiza o histórico.
2. Fechamento diário automático:
   - O bot envia mensagem no fim do dia perguntando o faturamento bruto das plataformas.
   - Gera um DRE simples: Faturamento Bruto - Combustível - Custo Estimado de Pneu/Óleo = Lucro Líquido Real.
3. Diferenciação de km particular vs trabalho de forma simples.

Crie a estrutura em Python (FastAPI) pronta para conexão do webhook.
```
