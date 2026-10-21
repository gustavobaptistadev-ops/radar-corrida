package com.motorista.radar

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Configuração em memória (lida 1x) + persistência simples em SharedPreferences. */
object Prefs {
    @Volatile private var cache: Config? = null

    private fun sp(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences("radar", Context.MODE_PRIVATE)

    private fun SharedPreferences.d(key: String, def: Double) =
        getString(key, null)?.toDoubleOrNull() ?: def

    fun load(ctx: Context): Config = cache ?: sp(ctx).let { p ->
        val def = Config()
        Config(
            fuelPrice = p.d("fuel", def.fuelPrice),
            kmPerLiter = p.d("kml", def.kmPerLiter),
            otherCostKm = p.d("other", def.otherCostKm),
            minPerKm = p.d("minKm", def.minPerKm),
            minPerHour = p.d("minH", def.minPerHour),
            maxPickupKm = p.d("maxPick", def.maxPickupKm),
        )
    }.also { cache = it }

    fun save(ctx: Context, c: Config) {
        sp(ctx).edit()
            .putString("fuel", c.fuelPrice.toString())
            .putString("kml", c.kmPerLiter.toString())
            .putString("other", c.otherCostKm.toString())
            .putString("minKm", c.minPerKm.toString())
            .putString("minH", c.minPerHour.toString())
            .putString("maxPick", c.maxPickupKm.toString())
            .apply()
        cache = c
    }

    fun speak(ctx: Context) = sp(ctx).getBoolean("speak", false)
    fun setSpeak(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("speak", v).apply()

    /**
     * Registra abastecimento: calcula litros e, com o hodômetro anterior,
     * estima o consumo real (km/L) — inclui rodagem pessoal, que também gasta combustível.
     * O consumo é suavizado (70% antigo / 30% novo) porque tanques parciais geram ruído.
     */
    fun refuel(ctx: Context, paid: Double, pricePerLiter: Double, odometer: Double, fuelType: String): String {
        val p = sp(ctx)
        val liters = paid / pricePerLiter
        val lastOdo = p.d("lastOdo", 0.0)
        var c = load(ctx).copy(fuelPrice = pricePerLiter)
        val msg = StringBuilder("≈ ${liters.br(1)} L de $fuelType")

        if (lastOdo > 0 && odometer > lastOdo) {
            val driven = odometer - lastOdo
            val measured = driven / liters
            msg.append("\n${driven.br(0)} km desde o último abastecimento")
            if (measured in 3.0..30.0) {
                val smoothed = c.kmPerLiter * 0.7 + measured * 0.3
                c = c.copy(kmPerLiter = smoothed)
                msg.append("\nConsumo atualizado: ${smoothed.br(1)} km/L")
            }
        }
        save(ctx, c)

        val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
        val line = "$date;$fuelType;${paid};${pricePerLiter};${odometer}"
        val history = (p.getString("history", "") ?: "").lines().filter { it.isNotBlank() }.takeLast(59) + line
        p.edit().putString("lastOdo", odometer.toString())
            .putString("history", history.joinToString("\n")).apply()
        return msg.toString()
    }

    fun history(ctx: Context): String = sp(ctx).getString("history", "") ?: ""
}
