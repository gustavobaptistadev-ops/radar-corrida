package com.motorista.radar

import java.util.Locale

/** Corrida lida da tela. Distâncias em km, tempo total (busca + viagem) em minutos. */
data class Ride(val price: Double, val pickupKm: Double, val tripKm: Double, val totalMin: Int) {
    val totalKm: Double get() = pickupKm + tripKm
}

/** Custos do carro e metas do motorista. */
data class Config(
    val fuelPrice: Double = 5.80,   // R$/litro
    val kmPerLiter: Double = 10.0,  // consumo médio
    val otherCostKm: Double = 0.25, // manutenção, pneus, depreciação (R$/km)
    val minPerKm: Double = 2.00,    // meta mínima R$/km (bruto)
    val minPerHour: Double = 35.0,  // meta mínima de lucro R$/hora
    val maxPickupKm: Double = 5.0,  // busca máxima aceitável
) {
    val costPerKm: Double get() = fuelPrice / kmPerLiter.coerceAtLeast(1.0) + otherCostKm
}

enum class Signal(val emoji: String, val word: String, val color: Int) {
    GREEN("🟢", "Verde", 0xE61B873F.toInt()),
    YELLOW("🟡", "Amarelo", 0xE6B77B00.toInt()),
    RED("🔴", "Vermelho", 0xE6C62828.toInt()),
}

data class Verdict(
    val signal: Signal,
    val perKm: Double,    // bruto R$/km (contando a busca)
    val perHour: Double,  // lucro R$/hora
    val profit: Double,   // lucro estimado (valor - custo do km rodado)
    val reason: String,   // motivo curto quando não é verde
)

/**
 * Extrai valor, distâncias e tempos do texto do card da corrida.
 * Só regex sobre uma String: microssegundos, sem OCR, sem rede.
 */
object RideParser {
    private val PRICE = Regex("""R\$\s*(\d{1,4}(?:[.,]\d{1,2})?)""")
    private val DIST = Regex("""(\d{1,3}(?:[.,]\d{1,2})?)\s*(km|m)\b""", RegexOption.IGNORE_CASE)
    private val HOURS = Regex("""(\d{1,2})\s*h\b(?:\s*(\d{1,2})\s*min)?""", RegexOption.IGNORE_CASE)
    private val MINUTES = Regex("""(\d{1,3})\s*min""", RegexOption.IGNORE_CASE)

    fun parse(text: String): Ride? {
        // Maior valor em R$ = valor da corrida (bônus/promoções costumam ser menores).
        val price = PRICE.findAll(text).mapNotNull { num(it.groupValues[1]) }.maxOrNull() ?: return null
        if (price <= 0.0) return null

        // Ordem na tela: 1ª distância = busca do passageiro, demais = viagem.
        val dists = DIST.findAll(text).mapNotNull { m ->
            val v = num(m.groupValues[1]) ?: return@mapNotNull null
            if (m.groupValues[2].equals("m", ignoreCase = true)) v / 1000.0 else v
        }.toList()
        if (dists.isEmpty()) return null

        var minutes = 0
        val rest = HOURS.replace(text) { m ->
            minutes += m.groupValues[1].toInt() * 60 + (m.groupValues[2].toIntOrNull() ?: 0)
            " "
        }
        minutes += MINUTES.findAll(rest).sumOf { it.groupValues[1].toInt() }

        val pickup = if (dists.size > 1) dists[0] else 0.0
        val trip = if (dists.size > 1) dists.drop(1).sum() else dists[0]
        return Ride(price, pickup, trip, minutes)
    }

    private fun num(s: String): Double? = s.replace(',', '.').toDoubleOrNull()
}

object RideEvaluator {
    fun evaluate(r: Ride, c: Config): Verdict {
        val km = r.totalKm.coerceAtLeast(0.1)
        val profit = r.price - km * c.costPerKm
        val perKm = r.price / km
        val perHour = if (r.totalMin > 0) profit / (r.totalMin / 60.0) else 0.0

        val reasons = ArrayList<String>(3)
        if (perKm < c.minPerKm) reasons += "R$/km baixo"
        if (r.totalMin > 0 && perHour < c.minPerHour) reasons += "R$/h baixo"
        if (r.pickupKm > c.maxPickupKm) reasons += "busca longe"

        val signal = when {
            profit <= 0 -> Signal.RED
            reasons.isEmpty() -> Signal.GREEN
            perKm < c.minPerKm * 0.8 ||
                (r.totalMin > 0 && perHour < c.minPerHour * 0.8) ||
                r.pickupKm > c.maxPickupKm * 1.5 -> Signal.RED
            else -> Signal.YELLOW
        }
        val reason = if (profit <= 0) "prejuízo" else reasons.joinToString(" • ")
        return Verdict(signal, perKm, perHour, profit, reason)
    }
}

private val BR = Locale("pt", "BR")
fun Double.br(decimals: Int = 2): String = String.format(BR, "%.${decimals}f", this)
