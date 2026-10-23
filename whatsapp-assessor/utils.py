import os
import json
import sqlite3
from datetime import datetime
from typing import Optional, Dict, Any
import google.generativeai as genai
from dotenv import load_dotenv

load_dotenv()

GEMINI_API_KEY = os.getenv("GEMINI_API_KEY", "")
if GEMINI_API_KEY:
    genai.configure(api_key=GEMINI_API_KEY)

DB_PATH = os.path.join(os.path.dirname(__file__), "radar_assessor.db")

def init_db():
    conn = sqlite3.connect(DB_PATH)
    cur = conn.cursor()
    cur.execute("""
        CREATE TABLE IF NOT EXISTS abastecimentos (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            data TEXT,
            tipo_combustivel TEXT,
            valor_pago REAL,
            preco_litro REAL,
            litros REAL,
            odometro REAL,
            km_rodados REAL,
            consumo_calculado REAL,
            observacao TEXT
        )
    """)
    cur.execute("""
        CREATE TABLE IF NOT EXISTS fechamento_diario (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            data TEXT,
            bruto_uber REAL,
            bruto_99 REAL,
            bruto_outros REAL,
            total_bruto REAL,
            gasto_combustivel REAL,
            manutencao_provisionada REAL,
            lucro_liquido REAL,
            km_total REAL,
            km_particular REAL,
            observacao TEXT
        )
    """)
    conn.commit()
    conn.close()

init_db()

SYSTEM_PROMPT = """
Você é o Assessor Financeiro de Inteligência Artificial do motorista de aplicativo do Radar Corrida.
O motorista envia mensagens de texto ou áudios transcritos gravados no trânsito ou no posto.

Sua tarefa é analisar a mensagem e extrair dados estruturados em JSON para duas operações possíveis:

1. ABASTECIMENTO: Quando o motorista menciona que abasteceu.
Exemplo: "Botei 50 no etanol a 3,89 odometro 145200"
Campos JSON esperados:
{
  "intencao": "abastecimento",
  "valor_pago": 50.0,
  "preco_litro": 3.89,
  "tipo_combustivel": "Etanol",
  "odometro": 145200.0,
  "observacao": ""
}

2. FECHAMENTO: Quando o motorista relata o ganho do dia ou encerra o turno.
Exemplo: "Fechei o dia fiz 280 na uber e 120 na 99 gastei 25 de almoço rodei 180 km sendo 20 km pessoal"
Campos JSON esperados:
{
  "intencao": "fechamento",
  "bruto_uber": 280.0,
  "bruto_99": 120.0,
  "bruto_outros": 0.0,
  "km_total": 180.0,
  "km_particular": 20.0,
  "gastos_extras": 25.0,
  "observacao": "almoco 25"
}

3. OUTRO: Se for apenas uma dúvida ou conversa.
{
  "intencao": "conversa",
  "resposta": "Texto de resposta amigável e direto"
}

Responda SEMPRE E APENAS o bloco JSON válido. Sem explicações ou markdown adicionais.
"""

def interpretar_mensagem_ia(texto: str) -> Dict[str, Any]:
    if not GEMINI_API_KEY:
        # Modo fallback simples caso a chave da API não esteja configurada no .env
        return fallback_parser(texto)

    try:
        model = genai.GenerativeModel("gemini-1.5-flash", system_instruction=SYSTEM_PROMPT)
        resp = model.generate_content(texto)
        raw = resp.text.strip()
        if raw.startswith("```json"):
            raw = raw[7:-3].strip()
        elif raw.startswith("```"):
            raw = raw[3:-3].strip()
        return json.loads(raw)
    except Exception as e:
        print(f"Erro no Gemini: {e}")
        return fallback_parser(texto)

def fallback_parser(texto: str) -> Dict[str, Any]:
    # Parser de contingência por regex caso a API do Gemini esteja sem internet ou sem chave
    t = texto.lower()
    if "abastec" in t or "botei" in t or "coloquei" in t or "litro" in t:
        return {
            "intencao": "abastecimento",
            "valor_pago": 0.0,
            "preco_litro": 0.0,
            "tipo_combustivel": "Etanol" if "etanol" in t else "Gasolina",
            "odometro": 0.0,
            "observacao": texto
        }
    return {
        "intencao": "conversa",
        "resposta": "Recebi seu relato! Configure sua chave GEMINI_API_KEY no arquivo .env para ativar a inteligência completa."
    }

def salvar_abastecimento(dados: Dict[str, Any]) -> str:
    valor = float(dados.get("valor_pago", 0.0))
    preco = float(dados.get("preco_litro", 0.0))
    odometro = float(dados.get("odometro", 0.0))
    tipo = dados.get("tipo_combustivel", "Etanol")
    litros = valor / preco if preco > 0 else 0.0

    conn = sqlite3.connect(DB_PATH)
    cur = conn.cursor()

    cur.execute("SELECT odometro FROM abastecimentos ORDER BY id DESC LIMIT 1")
    row = cur.fetchone()
    last_odo = row[0] if row else 0.0

    km_rodados = 0.0
    consumo = 0.0
    msg_consumo = ""
    if last_odo > 0 and odometro > last_odo:
        km_rodados = odometro - last_odo
        if litros > 0:
            consumo = km_rodados / litros
            msg_consumo = f"\n🚗 Rodou {km_rodados:.1f} km (Consumo médio: {consumo:.2f} km/L)"

    data_hoje = datetime.now().strftime("%Y-%m-%d %H:%M")
    cur.execute("""
        INSERT INTO abastecimentos 
        (data, tipo_combustivel, valor_pago, preco_litro, litros, odometro, km_rodados, consumo_calculado, observacao)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
    """, (data_hoje, tipo, valor, preco, litros, odometro, km_rodados, consumo, dados.get("observacao", "")))
    conn.commit()
    conn.close()

    return f"⛽ *Abastecimento Registrado!*\n• {litros:.2f} Litros de {tipo}\n• Total: R$ {valor:.2f} (a R$ {preco:.2f}/L)\n• Hodômetro: {odometro:,.0f} km{msg_consumo}"

def salvar_fechamento(dados: Dict[str, Any], custo_km_base: float = 0.55) -> str:
    uber = float(dados.get("bruto_uber", 0.0))
    n99 = float(dados.get("bruto_99", 0.0))
    outros = float(dados.get("bruto_outros", 0.0))
    total_bruto = uber + n99 + outros

    km_total = float(dados.get("km_total", 0.0))
    km_particular = float(dados.get("km_particular", 0.0))
    km_trabalho = max(0.0, km_total - km_particular)

    custo_estimado = km_trabalho * custo_km_base
    lucro_liquido = total_bruto - custo_estimado

    data_hoje = datetime.now().strftime("%Y-%m-%d")
    conn = sqlite3.connect(DB_PATH)
    cur = conn.cursor()
    cur.execute("""
        INSERT INTO fechamento_diario
        (data, bruto_uber, bruto_99, bruto_outros, total_bruto, gasto_combustivel, manutencao_provisionada, lucro_liquido, km_total, km_particular, observacao)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    """, (data_hoje, uber, n99, outros, total_bruto, custo_estimado * 0.7, custo_estimado * 0.3, lucro_liquido, km_total, km_particular, dados.get("observacao", "")))
    conn.commit()
    conn.close()

    return f"""📊 *FECHAMENTO DO DIA CONCLUÍDO!*
💰 *Faturamento Bruto:* R$ {total_bruto:.2f}
  - Uber: R$ {uber:.2f}
  - 99: R$ {n99:.2f}

🛣️ *Rodagem:* {km_total:.1f} km ({km_trabalho:.1f} km trabalho | {km_particular:.1f} km particular)
⚙️ *Custo Operacional Estimado:* R$ {custo_estimado:.2f}
💵 *LUCRO LÍQUIDO REAL:* R$ {lucro_liquido:.2f}

{f"Média por km líquido: R$ {(lucro_liquido / km_trabalho):.2f}/km" if km_trabalho > 0 else ""}
Bom descanso, motorista! 🚀"""
