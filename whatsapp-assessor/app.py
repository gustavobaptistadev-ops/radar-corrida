from fastapi import FastAPI, Request, HTTPException
from pydantic import BaseModel
from typing import Optional
from utils import interpretar_mensagem_ia, salvar_abastecimento, salvar_fechamento

app = FastAPI(title="Radar Corrida - Assessor WhatsApp", version="1.0.0")

class MensagemEntrada(BaseModel):
    texto: str
    remetente: Optional[str] = None

@app.get("/")
def status():
    return {
        "status": "online",
        "servico": "Radar Corrida - Assessor Financeiro WhatsApp",
        "ia": "Google Gemini 1.5 Flash (Off-Device)"
    }

@app.post("/webhook")
async def receber_mensagem(msg: MensagemEntrada):
    """
    Endpoint para integração com qualquer gateway de WhatsApp
    (Evolution API, Baileys, Z-API, WhatsApp Cloud API).
    """
    texto = msg.texto.strip()
    if not texto:
        raise HTTPException(status_code=400, detail="Texto vazio")

    # A IA interpreta se é abastecimento, fechamento ou dúvida
    analise = interpretar_mensagem_ia(texto)
    intencao = analise.get("intencao")

    if intencao == "abastecimento":
        resposta_texto = salvar_abastecimento(analise)
    elif intencao == "fechamento":
        resposta_texto = salvar_fechamento(analise)
    else:
        resposta_texto = analise.get("resposta", "Entendido! Qualquer dúvida ou abastecimento, só mandar.")

    return {
        "intencao_detectada": intencao,
        "dados_extraidos": analise,
        "resposta_whatsapp": resposta_texto
    }

@app.post("/webhook/evolution")
async def webhook_evolution(request: Request):
    """
    Webhook nativo compatível diretamente com a Evolution API (JSON padrão do WhatsApp).
    """
    payload = await request.json()
    try:
        # Extrai o texto da mensagem do formato da Evolution API
        msg_obj = payload.get("data", {}).get("message", {})
        texto = msg_obj.get("conversation") or msg_obj.get("extendedTextMessage", {}).get("text", "")
        if not texto:
            return {"status": "ignorado", "motivo": "sem texto"}

        analise = interpretar_mensagem_ia(texto)
        intencao = analise.get("intencao")

        if intencao == "abastecimento":
            resposta = salvar_abastecimento(analise)
        elif intencao == "fechamento":
            resposta = salvar_fechamento(analise)
        else:
            resposta = analise.get("resposta", "Olá! Sou seu assessor financeiro do Radar Corrida.")

        return {
            "status": "sucesso",
            "resposta": resposta
        }
    except Exception as e:
        return {"status": "erro", "detalhe": str(e)}
