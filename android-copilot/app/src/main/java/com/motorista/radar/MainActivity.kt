package com.motorista.radar

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.*

/**
 * Tela Moderna, Responsiva e Objetiva em Modo Escuro nativo.
 * Mantém tamanho de APK ultraleve (< 50 KB) sem bibliotecas pesadas.
 */
class MainActivity : Activity() {

    private lateinit var root: LinearLayout
    private lateinit var statusCard: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var btnEnableAcc: Button
    private lateinit var spCars: Spinner
    private lateinit var fuelPriceField: EditText
    private lateinit var kmlField: EditText
    private lateinit var otherCostField: EditText
    private lateinit var minKmField: EditText
    private lateinit var minHField: EditText
    private lateinit var maxPickField: EditText
    private lateinit var minRatField: EditText
    private lateinit var costPerKmBadge: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dp = resources.displayMetrics.density

        // Root com tema escuro moderno
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF121212.toInt())
            setPadding((16 * dp).toInt(), (16 * dp).toInt(), (16 * dp).toInt(), (24 * dp).toInt())
        }

        val c = Prefs.load(this)

        // Cabeçalho moderno
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, (12 * dp).toInt())
        }
        val appTitle = TextView(this).apply {
            text = "⚡ Radar Corrida"
            setTextColor(0xFFFFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        val appSubtitle = TextView(this).apply {
            text = "Assistente Inteligente & Ultraleve para Motoristas"
            setTextColor(0xFF9E9E9E.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        }
        header.addView(appTitle)
        header.addView(appSubtitle)
        root.addView(header)

        // Status Card
        statusCard = createCard(dp).apply {
            orientation = LinearLayout.VERTICAL
        }
        statusText = TextView(this).apply {
            setTextColor(0xFFFFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        }
        btnEnableAcc = createPrimaryButton("ATIVAR RADAR (ACESSIBILIDADE)", dp) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        statusCard.addView(statusText)
        statusCard.addView(btnEnableAcc)
        root.addView(statusCard)

        // Seção: Seu Veículo (Lista de Carros de Brasília)
        val carCard = createCard(dp)
        carCard.addView(createSectionTitle("🚗 Selecionar Veículo (Brasília / DF)", dp))

        spCars = Spinner(this).apply {
            val names = CarPresets.LIST.map { it.name }
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, names)
            setSelection(Prefs.carIndex(this@MainActivity))
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    Prefs.setCarIndex(this@MainActivity, position)
                    if (position > 0) {
                        val car = CarPresets.LIST[position]
                        val isGas = Prefs.fuelTypeIndex(this@MainActivity) == 1
                        val avgKml = if (isGas) car.kmPerLiterGasolina else car.kmPerLiterEtanol
                        kmlField.setText(avgKml.toString())
                        otherCostField.setText(car.otherCostKm.toString())
                        updateCostBadge()
                    }
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }
        carCard.addView(spCars)

        // Tipo de combustível rápido
        val rgFuel = RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
            setPadding(0, (8 * dp).toInt(), 0, (8 * dp).toInt())
        }
        val fuels = listOf("Etanol", "Gasolina", "GNV")
        fuels.forEachIndexed { idx, f ->
            rgFuel.addView(RadioButton(this).apply {
                text = f
                setTextColor(0xFFE0E0E0.toInt())
                id = idx + 100
                isChecked = idx == Prefs.fuelTypeIndex(this@MainActivity)
                setOnCheckedChangeListener { _, checked ->
                    if (checked) {
                        Prefs.setFuelTypeIndex(this@MainActivity, idx)
                        val cIdx = spCars.selectedItemPosition
                        if (cIdx > 0) {
                            val car = CarPresets.LIST[cIdx]
                            val avgKml = if (idx == 1) car.kmPerLiterGasolina else car.kmPerLiterEtanol
                            kmlField.setText(avgKml.toString())
                            updateCostBadge()
                        }
                    }
                }
            })
        }
        carCard.addView(rgFuel)

        fuelPriceField = createField(carCard, "Preço do Combustível (R$/L)", c.fuelPrice.toString(), dp)
        kmlField = createField(carCard, "Consumo Médio do Carro (km/L)", c.kmPerLiter.toString(), dp)
        otherCostField = createField(carCard, "Manutenção/Depreciação por km (R$)", c.otherCostKm.toString(), dp)

        costPerKmBadge = TextView(this).apply {
            setTextColor(0xFF4CAF50.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, (6 * dp).toInt(), 0, (4 * dp).toInt())
        }
        carCard.addView(costPerKmBadge)
        updateCostBadge()

        root.addView(carCard)

        // Seção: Metas de Rentabilidade
        val targetCard = createCard(dp)
        targetCard.addView(createSectionTitle("🎯 Suas Metas de Lucro", dp))
        minKmField = createField(targetCard, "Mínimo R$/km Bruto", c.minPerKm.toString(), dp)
        minHField = createField(targetCard, "Mínimo Lucro R$/hora", c.minPerHour.toString(), dp)
        maxPickField = createField(targetCard, "Busca Máxima do Passageiro (km)", c.maxPickupKm.toString(), dp)
        minRatField = createField(targetCard, "Nota Mínima do Passageiro (ex: 4.75)", c.minRating.toString(), dp)

        val chkSpeak = CheckBox(this).apply {
            text = "Falar resumo em voz alta no som/fone"
            setTextColor(0xFFFFFFFF.toInt())
            isChecked = Prefs.speak(this@MainActivity)
        }
        val chkVoiceAlerts = CheckBox(this).apply {
            text = "Falar APENAS alertas (corridas amarelas/vermelhas)"
            setTextColor(0xFFB0BEC5.toInt())
            isChecked = c.voiceOnlyAlerts
        }
        targetCard.addView(chkSpeak)
        targetCard.addView(chkVoiceAlerts)

        val btnSave = createPrimaryButton("SALVAR PREFERÊNCIAS", dp) {
            val fp = fuelPriceField.num() ?: c.fuelPrice
            val kml = kmlField.num() ?: c.kmPerLiter
            val oth = otherCostField.num() ?: c.otherCostKm
            val newCfg = Config(
                fuelPrice = fp,
                kmPerLiter = kml,
                otherCostKm = oth,
                minPerKm = minKmField.num() ?: c.minPerKm,
                minPerHour = minHField.num() ?: c.minPerHour,
                maxPickupKm = maxPickField.num() ?: c.maxPickupKm,
                minRating = minRatField.num() ?: c.minRating,
                voiceOnlyAlerts = chkVoiceAlerts.isChecked
            )
            Prefs.save(this, newCfg)
            Prefs.setSpeak(this, chkSpeak.isChecked)
            updateCostBadge()
            Toast.makeText(this, "✅ Salvo! Custo operacional: R$ ${newCfg.costPerKm.br()}/km", Toast.LENGTH_SHORT).show()
        }
        targetCard.addView(btnSave)
        root.addView(targetCard)

        // Seção: Registro Rápido de Abastecimento
        val refuelCard = createCard(dp)
        refuelCard.addView(createSectionTitle("⛽ Registrar Abastecimento", dp))
        val paidField = createField(refuelCard, "Valor Total Pago (R$)", "", dp)
        val priceLField = createField(refuelCard, "Preço por Litro no Posto (R$)", "", dp)
        val odoField = createField(refuelCard, "Hodômetro Atual do Carro (km)", "", dp)
        val refuelResult = TextView(this).apply {
            setTextColor(0xFF81C784.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        }
        val btnRefuel = createSecondaryButton("CALCULAR & ATUALIZAR MÉDIA", dp) {
            val p = paidField.num()
            val l = priceLField.num()
            val o = odoField.num()
            if (p == null || l == null || o == null || l <= 0) {
                Toast.makeText(this, "Preencha valor pago, preço/litro e hodômetro", Toast.LENGTH_SHORT).show()
                return@createSecondaryButton
            }
            val fuelName = fuels[Prefs.fuelTypeIndex(this)]
            refuelResult.text = Prefs.refuel(this, p, l, o, fuelName)
            val updatedCfg = Prefs.load(this)
            fuelPriceField.setText(updatedCfg.fuelPrice.toString())
            kmlField.setText(updatedCfg.kmPerLiter.br(1).replace(',', '.'))
            updateCostBadge()
            paidField.text.clear()
            priceLField.text.clear()
            odoField.text.clear()
        }
        refuelCard.addView(btnRefuel)
        refuelCard.addView(refuelResult)
        root.addView(refuelCard)

        setContentView(ScrollView(this).apply {
            setBackgroundColor(0xFF121212.toInt())
            addView(root)
        })
    }

    override fun onResume() {
        super.onResume()
        val dp = resources.displayMetrics.density
        val on = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?.contains("$packageName/") == true

        if (on) {
            statusCard.background = GradientDrawable().apply {
                setColor(0xFF1B5E20.toInt())
                cornerRadius = 14 * dp
            }
            statusText.text = "🟢 Radar Conectado e Ativo!\nPode abrir a Uber/99 e ficar online. O selo aparecerá quando tocar viagem."
            btnEnableAcc.visibility = View.GONE
        } else {
            statusCard.background = GradientDrawable().apply {
                setColor(0xFF37474F.toInt())
                cornerRadius = 14 * dp
            }
            statusText.text = "⚠️ Radar Desligado\nToque no botão abaixo para ativar em Acessibilidade:"
            btnEnableAcc.visibility = View.VISIBLE
        }
    }

    private fun updateCostBadge() {
        val fp = fuelPriceField.num() ?: 5.80
        val kml = (kmlField.num() ?: 10.0).coerceAtLeast(1.0)
        val oth = otherCostField.num() ?: 0.25
        val cost = (fp / kml) + oth
        costPerKmBadge.text = "💰 Custo Operacional Real: R$ ${cost.br()}/km"
    }

    // --- Helpers de Design Moderno & Responsivo ---
    private fun createCard(dp: Float): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(0xFF1E1E1E.toInt())
                cornerRadius = 14 * dp
            }
            setPadding((14 * dp).toInt(), (14 * dp).toInt(), (14 * dp).toInt(), (14 * dp).toInt())
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(0, 0, 0, (14 * dp).toInt())
            layoutParams = lp
        }
    }

    private fun createSectionTitle(t: String, dp: Float): TextView {
        return TextView(this).apply {
            text = t
            setTextColor(0xFFFFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, (8 * dp).toInt())
        }
    }

    private fun createField(parent: LinearLayout, label: String, defVal: String, dp: Float): EditText {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, (4 * dp).toInt(), 0, (4 * dp).toInt())
        }
        val tv = TextView(this).apply {
            text = label
            setTextColor(0xFFB0BEC5.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        }
        val et = EditText(this).apply {
            setText(defVal)
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFF757575.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            background = GradientDrawable().apply {
                setColor(0xFF2A2A2A.toInt())
                cornerRadius = 8 * dp
                setStroke((1 * dp).toInt(), 0xFF424242.toInt())
            }
            setPadding((10 * dp).toInt(), (8 * dp).toInt(), (10 * dp).toInt(), (8 * dp).toInt())
        }
        container.addView(tv)
        container.addView(et)
        parent.addView(container)
        return et
    }

    private fun createPrimaryButton(text: String, dp: Float, onClick: (View) -> Unit): Button {
        return Button(this).apply {
            this.text = text
            setTextColor(0xFF000000.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            background = GradientDrawable().apply {
                setColor(0xFF00E676.toInt()) // Verde neon moderno
                cornerRadius = 10 * dp
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (44 * dp).toInt()
            )
            lp.setMargins(0, (10 * dp).toInt(), 0, (4 * dp).toInt())
            layoutParams = lp
            setOnClickListener(onClick)
        }
    }

    private fun createSecondaryButton(text: String, dp: Float, onClick: (View) -> Unit): Button {
        return Button(this).apply {
            this.text = text
            setTextColor(0xFFFFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            background = GradientDrawable().apply {
                setColor(0xFF2C3842.toInt())
                cornerRadius = 10 * dp
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (40 * dp).toInt()
            )
            lp.setMargins(0, (8 * dp).toInt(), 0, 0)
            layoutParams = lp
            setOnClickListener(onClick)
        }
    }

    private fun EditText.num(): Double? = text.toString().replace(',', '.').toDoubleOrNull()
}
