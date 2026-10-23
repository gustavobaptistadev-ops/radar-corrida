package com.motorista.radar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RideTest {
    private val cfg = Config(
        fuelPrice = 4.0,
        kmPerLiter = 10.0,
        otherCostKm = 0.2, // custo total por km = R$ 0,60
        minPerKm = 2.0,
        minPerHour = 35.0,
        maxPickupKm = 5.0,
        minRating = 4.75
    )

    @Test
    fun uberCardComNotaEParada() {
        val text = "UberX R$ 28,50 4,89 ★ 5 min (1.8 km) de distância Viagem de 22 min (11.2 km) com 1 parada Aceitar"
        val r = RideParser.parse(text)
        assertNotNull(r)
        assertEquals(28.5, r!!.price, 0.001)
        assertEquals(1.8, r.pickupKm, 0.001)
        assertEquals(11.2, r.tripKm, 0.001)
        assertEquals(27, r.totalMin)
        assertEquals(1, r.stops)
        assertEquals(4.89, r.rating ?: 0.0, 0.001)
    }

    @Test
    fun uberCardComDinamicaEPedagio() {
        val text = "Comfort R$ 42,00 Alta demanda Pedágio incluído 4,95 ★ 4 min (1.2 km) Viagem de 16 min (8.0 km)"
        val r = RideParser.parse(text)
        assertNotNull(r)
        assertEquals(42.0, r!!.price, 0.001)
        assertTrue(r.hasDynamic)
        assertTrue(r.tollIncluded)
        val v = RideEvaluator.evaluate(r, cfg)
        assertEquals(Signal.GREEN, v.signal)
    }

    @Test
    fun card99PopComMetros() {
        val text = "99Pop R$ 19,80 800 m 3 min de busca Viagem 7,5 km 18 min Aceitar"
        val r = RideParser.parse(text)
        assertNotNull(r)
        assertEquals(19.80, r!!.price, 0.001)
        assertEquals(0.8, r.pickupKm, 0.001)
        assertEquals(7.5, r.tripKm, 0.001)
        assertEquals(21, r.totalMin)
    }

    @Test
    fun cardInDriveOferta() {
        val text = "inDrive Oferta: R$ 42,00 Distância até o cliente: 2,5 km Viagem estimada: 15 km 28 min"
        val r = RideParser.parse(text)
        assertNotNull(r)
        assertEquals(42.0, r!!.price, 0.001)
        assertEquals(2.5, r.pickupKm, 0.001)
        assertEquals(15.0, r.tripKm, 0.001)
        assertEquals(28, r.totalMin)
    }

    @Test
    fun card99NegociaComNotaEComissao() {
        val text = "99Negocia R$ 31,50 4.82 ★ Busca 2.1 km 6 min Viagem 9.8 km 22 min"
        val r = RideParser.parse(text)
        assertNotNull(r)
        assertEquals(31.50, r!!.price, 0.001)
        assertEquals(2.1, r.pickupKm, 0.001)
        assertEquals(9.8, r.tripKm, 0.001)
        assertEquals(4.82, r.rating ?: 0.0, 0.001)
    }

    @Test
    fun passageiroNotaMuitoBaixaGeraVermelho() {
        val r = Ride(price = 25.0, pickupKm = 1.0, tripKm = 8.0, totalMin = 18, rating = 4.52)
        val v = RideEvaluator.evaluate(r, cfg)
        assertEquals(Signal.RED, v.signal)
        assertTrue(v.reason.contains("nota"))
    }

    @Test
    fun buscaMuitoLongeGeraAlertaAmareloOuVermelho() {
        val r = Ride(price = 35.0, pickupKm = 6.5, tripKm = 9.0, totalMin = 25)
        val v = RideEvaluator.evaluate(r, cfg)
        assertEquals(Signal.YELLOW, v.signal)
        assertTrue(v.reason.contains("busca longe"))
    }

    @Test
    fun textoGenericoIgnorado() {
        assertNull(RideParser.parse("Você está online. Procurando viagens"))
        assertNull(RideParser.parse("Saldo diário: R$ 145,00"))
    }
}
