package com.motorista.radar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RideTest {
    private val cfg = Config(fuelPrice = 4.0, kmPerLiter = 10.0, otherCostKm = 0.2,
        minPerKm = 2.0, minPerHour = 35.0, maxPickupKm = 5.0) // custo = 0,60/km

    @Test fun uberCard() {
        val r = RideParser.parse("UberX R$ 18,50 4,92 ★ 6 min (2.1 km) de distância Viagem de 18 min (9.4 km) Aceitar")!!
        assertEquals(18.5, r.price, 0.001)
        assertEquals(2.1, r.pickupKm, 0.001)
        assertEquals(9.4, r.tripKm, 0.001)
        assertEquals(24, r.totalMin)
    }

    @Test fun metersAndHours() {
        val r = RideParser.parse("R$ 60,00 800 m 3 min 1 h 5 min 40,5 km")!!
        assertEquals(0.8, r.pickupKm, 0.001)
        assertEquals(40.5, r.tripKm, 0.001)
        assertEquals(68, r.totalMin)
    }

    @Test fun ignoresPerKmLabelAndClockTime() {
        val r = RideParser.parse("R$ 22,40 R$ 2,10/km 1,5 km 4 min chegada 18h30 9,2 km 21 min")!!
        assertEquals(22.4, r.price, 0.001)
        assertEquals(10.7, r.totalKm, 0.001)
        assertEquals(25, r.totalMin)
    }

    @Test fun notARide() {
        assertNull(RideParser.parse("Você está online. Procurando viagens"))
        assertNull(RideParser.parse("Saldo R$ 120,00"))
    }

    @Test fun green() {
        val v = RideEvaluator.evaluate(Ride(30.0, 1.0, 9.0, 20), cfg)
        assertEquals(Signal.GREEN, v.signal)
        assertEquals(24.0, v.profit, 0.001)
    }

    @Test fun redWhenPerKmVeryLow() {
        assertEquals(Signal.RED, RideEvaluator.evaluate(Ride(10.0, 2.0, 8.0, 20), cfg).signal)
    }

    @Test fun yellowWhenPickupALittleFar() {
        val v = RideEvaluator.evaluate(Ride(40.0, 6.0, 10.0, 25), cfg)
        assertEquals(Signal.YELLOW, v.signal)
        assertEquals("busca longe", v.reason)
    }
}
