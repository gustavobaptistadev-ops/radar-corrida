# Radar Corrida (Android)

Assistente para motorista de app: quando toca uma corrida na Uber, 99 ou inDrive, mostra um selo pequeno no canto da tela:

```
🟢 R$ 2,45/km  •  R$ 52/h
Lucro R$ 14,20 • 11,5 km • 24 min
```

🟢 vale a pena · 🟡 no limite (mostra o motivo) · 🔴 recusar (prejuízo, R$/km ou R$/h muito baixos, busca muito longe).

## Por que não esquenta o celular
- Lê só o **texto** da tela pelo Serviço de Acessibilidade: sem print, sem OCR, sem IA, sem internet.
- O Android só acorda o Radar para os apps de motorista (filtro de pacotes).
- Throttle de 250 ms, descarte de cards repetidos, limite de 400 nós lidos.
- Um único `TextView` reaproveitado e que ignora toques (não atrapalha o “Aceitar”).
- Nenhuma biblioteca em runtime, nenhuma permissão além da Acessibilidade.

## Recursos
- Custo real por km = combustível ÷ consumo + outros custos (manutenção, pneu, depreciação).
- Metas: mínimo R$/km, mínimo de lucro/hora, busca máxima.
- Abastecimento: valor pago + preço/L + hodômetro → litros e consumo real (km/L) atualizado sozinho.
- Simulador de corrida na tela inicial.
- Opcional: falar o resumo em voz alta.

## Estrutura
| Arquivo | Função |
|---|---|
| `app/src/main/java/com/motorista/radar/Ride.kt` | Parser do card + avaliação (Kotlin puro, testável) |
| `.../RadarService.kt` | Serviço de Acessibilidade + selo flutuante |
| `.../Prefs.kt` | Configurações e abastecimentos |
| `.../MainActivity.kt` | Tela de configuração |
| `app/src/test/.../RideTest.kt` | Testes do parser e do semáforo |

## Compilar
Requer JDK 17 + Android SDK (ou Android Studio). Na primeira vez, gere o wrapper: `gradle wrapper`.
```
gradlew testDebugUnitTest   # testes
gradlew assembleRelease     # APK enxuto em app/build/outputs/apk/release
```
No celular: instale o APK → abra o Radar → “Ativar Radar” → ative **Radar Corrida** em Acessibilidade.

## Limites conhecidos
- Os textos dos cards mudam com atualizações da Uber/99; o parser precisa ser validado com cards reais.
- Nome do pacote da 99 a confirmar no aparelho (`com.app99.driver` / `com.taxis99` estão no filtro).
