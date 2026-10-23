# Assessor Financeiro WhatsApp - Radar Corrida (Módulo 2)

Serviço backend com inteligência artificial (Google Gemini) para gestão de abastecimentos, consumo real do veículo e fechamento diário de faturamento via WhatsApp.

## Por que fica em um módulo separado?
Para respeitar a **Regra nº 1 do Radar Corrida**: o celular do motorista fica no suporte debaixo de sol e **NÃO PODE AQUECER**. Toda a inteligência artificial generativa, processamento de áudio/texto e relatórios rodam na nuvem/servidor, sem pesar no aparelho.

## Como Funciona:
1. **No Posto de Combustível:**
   - O motorista envia um áudio ou texto rápido no WhatsApp:
     > *"Botei 50 no etanol a 3,89, odômetro 145.200"*
   - O assessor calcula os litros (~12,85 L), registra o odômetro e calcula a média de km/L desde o último abastecimento.
2. **No Fim do Turno:**
   - O motorista manda os dados do dia:
     > *"Fechei o dia, deu 260 na Uber e 110 na 99. Rodei 170 km, sendo 20 km pra buscar a esposa."*
   - O assessor gera um DRE instantâneo:
     - Faturamento bruto (R$ 370,00)
     - Custos reais operacionais deduzidos
     - Separação de km particular vs trabalho
     - Lucro Líquido Real limpo no bolso!

## Como Rodar Localmente ou no Servidor:

```bash
cd whatsapp-assessor
python -m venv venv
# No Windows:
.\venv\Scripts\activate
# No Linux:
source venv/bin/activate

pip install -r requirements.txt
cp .env.example .env
# Adicione sua GEMINI_API_KEY no .env

uvicorn app:app --host 0.0.0.0 --port 8000
```

## Endpoints:
- `POST /webhook`: recebe `{"texto": "sua mensagem"}`
- `POST /webhook/evolution`: webhook direto para integração com a Evolution API.
