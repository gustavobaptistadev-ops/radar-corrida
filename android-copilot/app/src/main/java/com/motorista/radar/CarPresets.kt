package com.motorista.radar

/** Modelo de veículo pré-configurado com médias reais de consumo urbano. */
data class CarPreset(
    val name: String,
    val kmPerLiterEtanol: Double,
    val kmPerLiterGasolina: Double,
    val otherCostKm: Double = 0.25
)

object CarPresets {
    val LIST = listOf(
        CarPreset("Personalizado / Outro", 10.0, 14.0, 0.25),
        CarPreset("Chevrolet Onix 1.0 (Manual)", 9.5, 13.9, 0.23),
        CarPreset("Chevrolet Onix Plus 1.0 (Sedan)", 9.6, 14.0, 0.24),
        CarPreset("Hyundai HB20 1.0", 9.4, 13.5, 0.23),
        CarPreset("Hyundai HB20S 1.0 (Sedan)", 9.2, 13.2, 0.24),
        CarPreset("Fiat Cronos 1.3", 9.0, 13.0, 0.25),
        CarPreset("Fiat Argo 1.0", 9.3, 13.4, 0.23),
        CarPreset("Fiat Mobi 1.0", 9.8, 14.2, 0.20),
        CarPreset("Renault Kwid 1.0", 10.8, 15.3, 0.20),
        CarPreset("Renault Logan 1.0 / 1.6", 8.8, 12.8, 0.26),
        CarPreset("Volkswagen Polo 1.0 Track", 9.3, 13.5, 0.24),
        CarPreset("Volkswagen Gol 1.0", 9.1, 13.3, 0.22),
        CarPreset("Volkswagen Voyage 1.6", 8.0, 11.6, 0.26),
        CarPreset("Toyota Yaris 1.5 (Automático)", 8.8, 12.6, 0.27),
        CarPreset("Nissan Versa 1.6", 8.1, 11.8, 0.27)
    )
}
