package it.unibo.lam2026.parkmate.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import it.unibo.lam2026.parkmate.databinding.FragmentStatsBinding
import it.unibo.lam2026.parkmate.model.SessioneParcheggio
import it.unibo.lam2026.parkmate.viewmodel.ParcheggioViewModel
import java.util.Calendar
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.components.XAxis
import org.osmdroid.config.Configuration
import androidx.preference.PreferenceManager
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Polygon
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Color

class StatsFragment : Fragment() {

    private var _binding: FragmentStatsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ParcheggioViewModel by viewModels()

    private var listaCompletaParcheggi: List<SessioneParcheggio> = emptyList()

    // Memoria per sapere esattamente quali puntini disegnare quando si apre il full screen
    private var parcheggiCorrentiSullaMappa: List<SessioneParcheggio> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Inizializza la configurazione di osmdroid con il contesto dell'app
        Configuration.getInstance().load(
            requireContext(),
            PreferenceManager.getDefaultSharedPreferences(requireContext())
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStatsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val opzioniFiltro = listOf("Tutto lo storico", "Ultimo Mese", "Ultimi 6 Mesi", "Ultimo Anno")
        val adapterSpinner = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, opzioniFiltro)
        adapterSpinner.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerTimeFilter.adapter = adapterSpinner

        // --- CONFIGURAZIONE MAPPA HEATMAP ---
        binding.mapViewStats.setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK)
        binding.mapViewStats.setMultiTouchControls(true)
        binding.mapViewStats.controller.setZoom(15.0)

        // Centro di default (Bologna)
        binding.mapViewStats.controller.setCenter(GeoPoint(44.4949, 11.3426))

        // Effetto bianco e nero per la mappa
        val colorMatrix = ColorMatrix()
        colorMatrix.setSaturation(0f)
        val filter = ColorMatrixColorFilter(colorMatrix)
        binding.mapViewStats.overlayManager.tilesOverlay.setColorFilter(filter)

        // Pulsante Espandi
        binding.btnEspandiMappa.setOnClickListener {
            mostraMappaFullScreen()
        }

        viewModel.statisticheGlobaliParcheggi.observe(viewLifecycleOwner) { listaParcheggi ->
            if (listaParcheggi != null) {
                listaCompletaParcheggi = listaParcheggi
                aggiornaStatistiche(binding.spinnerTimeFilter.selectedItemPosition)

                val nomiVeicoli = mutableListOf("Tutti i veicoli")
                nomiVeicoli.addAll(listaParcheggi.map { it.veicoloNome }.distinct())

                val spinnerAdapter = ArrayAdapter(
                    requireContext(),
                    android.R.layout.simple_spinner_dropdown_item,
                    nomiVeicoli
                )
                binding.spinnerVehicleFilter.adapter = spinnerAdapter

                binding.spinnerVehicleFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                        val veicoloScelto = nomiVeicoli[position]

                        val listaFiltrata = if (veicoloScelto == "Tutti i veicoli") {
                            listaCompletaParcheggi
                        } else {
                            listaCompletaParcheggi.filter { it.veicoloNome == veicoloScelto }
                        }

                        impostaGrafico(listaFiltrata)

                        // Salviamo i dati in memoria e disegniamo la mappa piccola
                        parcheggiCorrentiSullaMappa = listaFiltrata
                        disegnaHeatmap(parcheggiCorrentiSullaMappa, binding.mapViewStats)

                        aggiornaCardParkingEffort(listaFiltrata)
                    }

                    override fun onNothingSelected(parent: AdapterView<*>?) {}
                }
            }
        }

        binding.spinnerTimeFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                aggiornaStatistiche(position)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    // --- Funzione per creare il Pop-Up a tutto schermo ---
    private fun mostraMappaFullScreen() {
        if (_binding == null) return

        val dialogFullScreen = android.app.Dialog(requireContext(), android.R.style.Theme_Light_NoTitleBar)
        val layoutContenitore = android.widget.FrameLayout(requireContext())

        val mappaGigante = org.osmdroid.views.MapView(requireContext())
        mappaGigante.setMultiTouchControls(true)
        mappaGigante.controller.setZoom(14.0)

        val colorMatrix = ColorMatrix()
        colorMatrix.setSaturation(0f)
        mappaGigante.overlayManager.tilesOverlay.setColorFilter(ColorMatrixColorFilter(colorMatrix))

        layoutContenitore.addView(
            mappaGigante,
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT
        )

        val btnChiudi = android.widget.ImageButton(requireContext())
        btnChiudi.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
        btnChiudi.setBackgroundColor(Color.WHITE)
        btnChiudi.setPadding(24, 24, 24, 24)

        val parametriBottone = android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = android.view.Gravity.TOP or android.view.Gravity.END
            setMargins(0, 48, 48, 0)
        }

        btnChiudi.setOnClickListener { dialogFullScreen.dismiss() }
        layoutContenitore.addView(btnChiudi, parametriBottone)

        dialogFullScreen.setContentView(layoutContenitore)
        dialogFullScreen.show()

        // Disegna i dati sulla mappa gigante passando l'istanza corretta
        disegnaHeatmap(parcheggiCorrentiSullaMappa, mappaGigante)
    }

    private fun aggiornaStatistiche(tipoFiltro: Int) {
        if (listaCompletaParcheggi.isEmpty()) {
            svuotaGrafica()
            return
        }

        val limiteTempo = Calendar.getInstance()
        val timestampLimite: Long = when (tipoFiltro) {
            1 -> { limiteTempo.add(Calendar.MONTH, -1); limiteTempo.timeInMillis }
            2 -> { limiteTempo.add(Calendar.MONTH, -6); limiteTempo.timeInMillis }
            3 -> { limiteTempo.add(Calendar.YEAR, -1); limiteTempo.timeInMillis }
            else -> 0L
        }

        val listaFiltrata = listaCompletaParcheggi.filter { it.startTimeStamp >= timestampLimite }

        if (listaFiltrata.isEmpty()) {
            svuotaGrafica()
            return
        }

        val totaliAssoluti = listaFiltrata.size
        val visibiliInStorico = listaFiltrata.count { !it.archiviato }

        binding.tvTotalParkings.text = "Eseguiti in totale: $totaliAssoluti"
        binding.tvHistoryParkings.text = "Visibili nello storico: $visibiliInStorico"

        val raggruppatoVeicoli = listaFiltrata.groupBy { it.veicoloNome }
        val maxSelezioniVeicolo = raggruppatoVeicoli.maxOfOrNull { it.value.size } ?: 0
        val veicoliPreferiti = raggruppatoVeicoli.filter { it.value.size == maxSelezioniVeicolo }.keys.joinToString(" / ")
        binding.tvMostUsedVehicle.text = veicoliPreferiti

        val raggruppatoTipi = listaFiltrata.groupBy { it.tipoParcheggio }
        val maxSelezioniTipo = raggruppatoTipi.maxOfOrNull { it.value.size } ?: 0
        val tipiPreferiti = raggruppatoTipi.filter { it.value.size == maxSelezioniTipo }.keys.joinToString(" / ")
        binding.tvMostFrequentType.text = tipiPreferiti
    }

    private fun svuotaGrafica() {
        binding.tvHistoryParkings.text = "Visibili nello storico: 0"
        binding.tvMostUsedVehicle.text = "Nessun dato"
        binding.tvMostFrequentType.text = "Nessun dato"
        binding.tvParkingScoreValue.text = "-- / 100"
        binding.tvParkingScoreComment.text = "Inizia a viaggiare per calcolare il tuo punteggio!"
        binding.barChartSoste.clear()

        binding.mapViewStats.overlays.clear()
        binding.mapViewStats.invalidate()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun impostaGrafico(listaParcheggi: List<SessioneParcheggio>) {
        val entries = ArrayList<BarEntry>()
        val mesi = arrayOf("Gen", "Feb", "Mar", "Apr", "Mag", "Giu", "Lug", "Ago", "Set", "Ott", "Nov", "Dic")

        val raggruppatiPerMese = listaParcheggi.groupBy { sosta ->
            val cal = Calendar.getInstance()
            cal.timeInMillis = sosta.startTimeStamp
            cal.get(Calendar.MONTH)
        }

        for (i in 0..11) {
            val numeroSoste = raggruppatiPerMese[i]?.size ?: 0
            entries.add(BarEntry(i.toFloat(), numeroSoste.toFloat()))
        }

        val dataSet = BarDataSet(entries, "Numero di soste")
        dataSet.color = Color.parseColor("#9C27B0")
        dataSet.valueTextSize = 12f

        dataSet.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return if (value > 0) value.toInt().toString() else ""
            }
        }

        binding.barChartSoste.data = BarData(dataSet)

        val xAxis = binding.barChartSoste.xAxis
        xAxis.valueFormatter = IndexAxisValueFormatter(mesi)
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(false)
        xAxis.granularity = 1f

        binding.barChartSoste.description.isEnabled = false
        binding.barChartSoste.axisRight.isEnabled = false

        val yAxis = binding.barChartSoste.axisLeft
        yAxis.granularity = 1f
        yAxis.axisMinimum = 0f
        yAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return value.toInt().toString()
            }
        }

        binding.barChartSoste.invalidate()
    }

    private fun getGridKey(lat: Double, lon: Double, cellSizeDeg: Double = 0.002): String {
        val gridLat = (lat / cellSizeDeg).toInt()
        val gridLon = (lon / cellSizeDeg).toInt()
        return "$gridLat,$gridLon"
    }

    private fun calcolaScoreMedioPerCella(lista: List<SessioneParcheggio>): Map<String, Double> {
        val perCella = mutableMapOf<String, MutableList<Int>>()
        for (sessione in lista) {
            val score = sessione.parkingEffortScore ?: continue
            val key = getGridKey(sessione.latitudine, sessione.longitudine)
            perCella.getOrPut(key) { mutableListOf() }.add(score)
        }
        return perCella.mapValues { (_, scores) -> scores.average() }
    }

    private fun colorePerScore(avgScore: Double): Int {
        val ratio = ((avgScore - 1) / 4.0).coerceIn(0.0, 1.0)
        val red = (255 * ratio).toInt()
        val green = (255 * (1 - ratio)).toInt()
        return Color.argb(180, red, green, 0)
    }

    // Sistemata: ora accetta esplicitamente su quale mappa disegnare!
    private fun disegnaHeatmap(listaParcheggi: List<SessioneParcheggio>, mappaTarget: org.osmdroid.views.MapView) {
        // 1. Puliamo i vecchi disegni sulla mappa target passata
        mappaTarget.overlays.removeAll { it is Polygon && it.id == "effort_circle" }

        // 2. Filtriamo solo i parcheggi che hanno effettivamente uno score calcolato
        val sessioniConScore = listaParcheggi.filter { it.parkingEffortScore != null }

        if (sessioniConScore.isEmpty()) {
            Toast.makeText(requireContext(), "Nessun dato di sforzo disponibile", Toast.LENGTH_SHORT).show()
            return
        }

        var ultimoCentro: GeoPoint? = null

        for (sosta in sessioniConScore) {
            val score = sosta.parkingEffortScore!!
            ultimoCentro = GeoPoint(sosta.latitudine, sosta.longitudine)

            val coloreHex = when (score) {
                1 -> "#404CAF50" // Verde
                2 -> "#408BC34A" // Verde chiaro
                3 -> "#40FFEB3B" // Giallo
                4 -> "#40FF9800" // Arancione
                5 -> "#40F44336" // Rosso
                else -> "#409E9E9E" // Grigio default
            }

            val cerchio = Polygon(mappaTarget).apply {
                id = "effort_circle"
                points = Polygon.pointsAsCircle(ultimoCentro, 150.0)
                fillColor = Color.parseColor(coloreHex)
                strokeColor = Color.TRANSPARENT
                strokeWidth = 0f
            }

            mappaTarget.overlays.add(cerchio)
        }

        if (ultimoCentro != null) {
            mappaTarget.controller.animateTo(ultimoCentro)
        }
        mappaTarget.invalidate()
    }

    private fun aggiornaCardParkingEffort(listaParcheggi: List<SessioneParcheggio>) {
        val sessioniConScore = listaParcheggi.filter { it.parkingEffortScore != null }

        if (sessioniConScore.isEmpty()) {
            binding.tvParkingScoreValue.text = "-- / 100"
            binding.tvParkingScoreComment.text = "Inizia a viaggiare per calcolare il tuo punteggio!"
            return
        }

        val sommaPunteggiCentesimi = sessioniConScore.sumOf { sosta ->
            when (sosta.parkingEffortScore) {
                1 -> 100
                2 -> 80
                3 -> 60
                4 -> 40
                5 -> 20
                else -> 0
            }
        }

        val mediaPunteggio = sommaPunteggiCentesimi.toDouble() / sessioniConScore.size
        val mediaArrotondata = Math.round(mediaPunteggio).toInt()

        binding.tvParkingScoreValue.text = "$mediaArrotondata / 100"

        val commentoDinamico = when {
            mediaArrotondata >= 85 -> "Bravissimo! Sei un mago del parcheggio. 🧙‍♂️"
            mediaArrotondata >= 65 -> "Buono! Trovi parcheggio senza troppi sforzi. 👍"
            mediaArrotondata >= 45 -> "Sforzo medio. Te la cavi abbastanza bene in città. 🏙️"
            else -> "Che fatica! Giri un po' troppo prima di fermarti. 🚙💨"
        }
        binding.tvParkingScoreComment.text = commentoDinamico
    }

    override fun onResume() {
        super.onResume()
        binding.mapViewStats.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.mapViewStats.onPause()
    }
}