package com.motorista.radar

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Tela única de configuração. Sem XML/AppCompat para manter o APK mínimo. */
class MainActivity : Activity() {

    private lateinit var root: LinearLayout
    private lateinit var status: TextView
    private lateinit var fuel: EditText
    private lateinit var kml: EditText
    private lateinit var other: EditText
    private lateinit var minKm: EditText
    private lateinit var minH: EditText
    private lateinit var maxPick: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = dp(16)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        val c = Prefs.load(this)

        status = TextView(this).apply { textSize = 16f }
        root.addView(status)
        button("Ativar Radar (Acessibilidade)") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        title("Carro e metas")
        fuel = field("Preço do combustível (R$/L)", c.fuelPrice)
        kml = field("Consumo médio (km/L)", c.kmPerLiter)
        other = field("Outros custos por km (manutenção, pneu, depreciação)", c.otherCostKm)
        minKm = field("Mínimo R$/km", c.minPerKm)
        minH = field("Mínimo de lucro R$/hora", c.minPerHour)
        maxPick = field("Busca máxima (km)", c.maxPickupKm)
        val speak = CheckBox(this).apply {
            text = "Falar o resumo em voz alta"
            isChecked = Prefs.speak(this@MainActivity)
        }
        root.addView(speak)
        button("Salvar") {
            Prefs.save(this, Config(
                fuelPrice = fuel.num() ?: c.fuelPrice,
                kmPerLiter = kml.num() ?: c.kmPerLiter,
                otherCostKm = other.num() ?: c.otherCostKm,
                minPerKm = minKm.num() ?: c.minPerKm,
                minPerHour = minH.num() ?: c.minPerHour,
                maxPickupKm = maxPick.num() ?: c.maxPickupKm,
            ))
            Prefs.setSpeak(this, speak.isChecked)
            toast("Salvo. Custo atual: R$ ${Prefs.load(this).costPerKm.br()}/km")
        }

        title("Abastecimento")
        val types = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        listOf("Etanol", "Gasolina", "GNV").forEachIndexed { i, t ->
            types.addView(RadioButton(this).apply { text = t; id = i + 1; isChecked = i == 0 })
        }
        root.addView(types)
        val paid = field("Valor pago (R$)", null)
        val price = field("Preço por litro (R$)", null)
        val odo = field("Hodômetro agora (km)", null)
        val refuelOut = TextView(this)
        button("Registrar abastecimento") {
            val p = paid.num(); val l = price.num(); val o = odo.num()
            if (p == null || l == null || o == null || l <= 0) { toast("Preencha os 3 campos"); return@button }
            val type = (types.findViewById<RadioButton>(types.checkedRadioButtonId)?.text ?: "Etanol").toString()
            refuelOut.text = Prefs.refuel(this, p, l, o, type)
            val nc = Prefs.load(this)
            fuel.setText(nc.fuelPrice.toString()); kml.setText(nc.kmPerLiter.br(1).replace(',', '.'))
            paid.text.clear(); price.text.clear(); odo.text.clear()
        }
        root.addView(refuelOut)

        title("Simular corrida")
        val sp = field("Valor (R$)", null)
        val sk = field("Km total (busca + viagem)", null)
        val sm = field("Minutos total", null)
        val simOut = TextView(this).apply { textSize = 16f }
        button("Avaliar") {
            val ride = Ride(sp.num() ?: return@button, 0.0, sk.num() ?: return@button, sm.num()?.toInt() ?: 0)
            val v = RideEvaluator.evaluate(ride, Prefs.load(this))
            simOut.text = "${v.signal.emoji} R$ ${v.perKm.br()}/km • R$ ${v.perHour.br(0)}/h\n" +
                "Lucro R$ ${v.profit.br()} ${if (v.reason.isEmpty()) "" else "• " + v.reason}"
        }
        root.addView(simOut)

        setContentView(ScrollView(this).apply { addView(root) })
    }

    override fun onResume() {
        super.onResume()
        val on = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?.contains("$packageName/") == true
        status.text = if (on) "✅ Radar ativo — abra a Uber/99 e fique online"
        else "⚠️ Radar desligado — toque no botão abaixo e ative \"Radar Corrida\""
    }

    // --- helpers de UI ---
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun title(t: String) = root.addView(TextView(this).apply {
        text = t; textSize = 18f; setPadding(0, dp(20), 0, dp(4))
    })

    private fun field(hint: String, value: Double?): EditText {
        root.addView(TextView(this).apply { text = hint })
        return EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            value?.let { setText(it.toString()) }
            root.addView(this)
        }
    }

    private fun button(t: String, onClick: (View) -> Unit) =
        root.addView(Button(this).apply { text = t; setOnClickListener(onClick) })

    private fun EditText.num(): Double? = text.toString().replace(',', '.').toDoubleOrNull()
    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
}
