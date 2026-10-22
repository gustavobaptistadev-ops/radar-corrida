package com.motorista.radar

import java.util.Locale

/**
 * Corrida lida da tela.
 * @param price Valor da corrida em R$
 * @param pickupKm Distância até o passageiro (km)
 * @param tripKm Distância da viagem com passageiro (km)
 * @param totalMin Tempo total estimado (busca + viagem) em minutos
 * @param stops Quantidade de paradas intermediárias detectadas
 * @param rating Nota do passageiro (ex: 4.85), se detectada
 * @param hasDynamic Indica se há tarifa dinâmica/multiplicador no card
 */
data class Ride(
    val price: Double,
    val pickupKm: Double,
    val tripKm: Double,
    val totalMin: Int,
    val stops: Int = 0,
    val rating: Double? = null,
    val hasDynamic: Boolean = false
) {
    val totalKm: Double get() = pickupKm + tripKm
}

/** Custos do veículo e metas do motorista. */
data class Config(
    val fuelPrice: Double = 5.80,   // R$/litro
    val kmPerLiter: Double = 10.0,  // consumo médio (km/L)
    val otherCostKm: Double = 0.25, // manutenção, pneus, óleo, depreciação (R$/km)
    val minPerKm: Double = 2.00,    // meta mínima R$/km (bruto)
    val minPerHour: Double = 35.0,  // meta mínima de lucro R$/hora
    val maxPickupKm: Double = 5.0,  // busca máxima aceitável em km
    val minRating: Double = 4.75    // nota mínima aceitável do passageiro
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
    val perKm: Double,     // bruto R$/km (considerando busca + viagem)
    val netPerKm: Double,  // lucro líquido R$/km
    val perHour: Double,   // lucro líquido projetado R$/hora
    val profit: Double,    // lucro líquido total estimado da corrida
    val reason: String     // justificativa rápida (ex: "busca longe", "passageiro 4.62★", "2 paradas")
)

/**
 * Parser resiliente para Uber, 99 e inDrive.
 * Opera exclusivamente com expressões regulares e busca por padrões de texto pré-compilados.
 * Zero OCR, zero alocações redundantes, execução em microssegundos.
 */
object RideParser {
    private val PRICE = Regex("""R\$\s*(\d{1,4}(?:[.,]\d{1,2})?)""")
    private val DIST = Regex("""(\d{1,3}(?:[.,]\d{1,2})?)\s*(km|m)\b""", RegexOption.IGNORE_CASE)
    private val HOURS = Regex("""(\d{1,2})\s*h\b(?:\s*(\d{1,2})\s*min)?""", RegexOption.IGNORE_CASE)
    private val MINUTES = Regex("""(\d{1,3})\s*min""", RegexOption.IGNORE_CASE)
    private val RATING = Regex("""([345][.,]\d{1,2})\s*★|★\s*([345][.,]\d{1,2})""")
    private val STOPS = Regex("""(\d+)\s*(?:paradas?|stops?)|\bcom\s*parada\b""", RegexOption.IGNORE_CASE)
    private val DYNAMIC = Regex("""\b(?:dinâmica|dinamica|preço\s*mais\s*alto|alta\s*demanda|\+\s*R\$\s*\d+)\b""", RegexOption.IGNORE_CASE)

    fun parse(text: String): Ride? {
        // Encontra o valor principal da corrida em R$
        val price = PRICE.findAll(text).mapNotNull { num(it.groupValues[1]) }.maxOrNull() ?: return null
        if (price <= 0.0) return null

        // Extrai todas as distâncias em km ou metros
        val dists = DIST.findAll(text).mapNotNull { m ->
            val v = num(m.groupValues[1]) ?: return@mapNotNull null
            if (m.groupValues[2].equals("m", ignoreCase = true)) v / 1000.0 else v
        }.toList()
        if (dists.isEmpty()) return null

        // Extrai tempo total (horas + minutos)
        var minutes = 0
        val rest = HOURS.replace(text) { m ->
            minutes += m.groupValues[1].toInt() * 60 + (m.groupValues[2].toIntOrNull() ?: 0)
            " "
        }
        minutes += MINUTES.findAll(rest).sumOf { it.groupValues[1].toInt() }

        // Múltiplas paradas
        var stopsCount = 0
        val stopMatch = STOPS.find(text)
        if (stopMatch != null) {
            val countStr = stopMatch.groupValues[1]
            stopsCount = countStr.toIntOrNull() ?: 1
        }

        // Nota do passageiro
        val ratingMatch = RATING.find(text)
        val rating = ratingMatch?.let {
            val rStr = if (it.groupValues[1].isNotEmpty()) it.groupValues[1] else it.groupValues[2]
            num(rStr)
        }

        // Indicador de tarifa dinâmica
        val hasDynamic = DYNAMIC.containsMatchIn(text)

        val pickup = if (dists.size > 1) dists[0] else 0.0
        val trip = if (dists.size > 1) dists.drop(1).sum() else dists[0]

        return Ride(
            price = price,
            pickupKm = pickup,
            tripKm = trip,
            totalMin = minutes,
            stops = stopsCount,
            rating = rating,
            hasDynamic = hasDynamic
        )
    }

    private fun num(s: String): Double? = s.replace(',', '.').toDoubleOrNull()
}

/**
 * Avaliador de Viabilidade da Corrida.
 * Calcula rentabilidade líquida, deduções de km e semáforo instantâneo.
 */
object RideEvaluator {
    fun evaluate(r: Ride, c: Config): Verdict {
        val km = r.totalKm.coerceAtLeast(0.1)
        val fuelAndMaintenanceCost = km * c.costPerKm
        val profit = r.price - fuelAndMaintenanceCost
        val perKm = r.price / km
        val netPerKm = profit / km
        val perHour = if (r.totalMin > 0) profit / (r.totalMin / 60.0) else 0.0

        val reasons = ArrayList<String>(4)
        if (perKm < c.minPerKm) reasons += "R$/km baixo"
        if (r.totalMin > 0 && perHour < c.minPerHour) reasons += "R$/h baixo"
        if (r.pickupKm > c.maxPickupKm) reasons += "busca longe (${r.pickupKm.br(1)}km)"
        if (r.rating != null && r.rating < c.minRating) reasons += "nota ${r.rating.br(2)}★"
        if (r.stops > 0) reasons += "${r.stops} parada(s)"

        val signal = when {
            profit <= 0 -> Signal.RED
            reasons.isEmpty() -> Signal.GREEN
            perKm < c.minPerKm * 0.8 ||
                (r.totalMin > 0 && perHour < c.minPerHour * 0.8) ||
                r.pickupKm > c.maxPickupKm * 1.5 ||
                (r.rating != null && r.rating < 4.60) -> Signal.RED
            else -> Signal.YELLOW
        }

        val reason = if (profit <= 0) "prejuízo" else reasons.joinToString(" • ")
        return Verdict(
            signal = signal,
            perKm = perKm,
            netPerKm = netPerKm,
            perHour = perHour,
            profit = profit,
            reason = reason
        )
    }
}

private val BR = Locale("pt", "BR")
fun Double.br(decimals: Int = 2): String = String.format(BR, "%.${decimals}f", this)
