package it.unibo.lam2026.parkmate.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import it.unibo.lam2026.parkmate.databinding.FragmentStatsBinding
import it.unibo.lam2026.parkmate.model.SessioneParcheggio // 1. IMPORT REALE AGGIORNATO!
import it.unibo.lam2026.parkmate.viewmodel.ParcheggioViewModel
import java.util.Calendar
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.components.XAxis
import org.osmdroid.config.Configuration
import androidx.preference.PreferenceManager
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Polygon
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter

class StatsFragment : Fragment() {

    private var _binding: FragmentStatsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ParcheggioViewModel by viewModels()

    // 2. Usiamo la classe corretta: SessioneParcheggio
    private var listaCompletaParcheggi: List<SessioneParcheggio> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStatsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Configura lo Spinner dei Filtri Temporali
        val opzioniFiltro = listOf("Tutto lo storico", "Ultimo Mese", "Ultimi 6 Mesi", "Ultimo Anno")
        val adapterSpinner = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, opzioniFiltro)
        adapterSpinner.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerTimeFilter.adapter = adapterSpinner

        // --- CONFIGURAZIONE MAPPA HEATMAP ---
        Configuration.getInstance().load(requireContext(), PreferenceManager.getDefaultSharedPreferences(requireContext()))

        binding.mapViewStats.setMultiTouchControls(true)
        binding.mapViewStats.controller.setZoom(14.0) // Zoom iniziale

        // IL TRUCCO DELLO SFONDO NEUTRO: Togliamo i colori alla mappa!
        val colorMatrix = ColorMatrix()
        colorMatrix.setSaturation(0f) // 0 = Bianco e nero puro!
        val filter = ColorMatrixColorFilter(colorMatrix)
        binding.mapViewStats.overlayManager.tilesOverlay.setColorFilter(filter)

        // Ascolta il database dei parcheggi
        viewModel.statisticheGlobaliParcheggi.observe(viewLifecycleOwner) { listaParcheggi ->
            if (listaParcheggi != null) {
                listaCompletaParcheggi = listaParcheggi
                aggiornaStatistiche(binding.spinnerTimeFilter.selectedItemPosition)

                // 1. Estraiamo i nomi unici dei veicoli (es. se hai parcheggiato 10 volte la Panda, "Panda" apparirà una volta sola)
                val nomiVeicoli = mutableListOf("Tutti i veicoli")
                nomiVeicoli.addAll(listaParcheggi.map { it.veicoloNome }.distinct())

                // 2. Riempiamo lo Spinner con i nomi
                val spinnerAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, nomiVeicoli)
                binding.spinnerVehicleFilter.adapter = spinnerAdapter

                // 3. Quando l'utente seleziona un veicolo dal menu a tendina...
                binding.spinnerVehicleFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                        val veicoloScelto = nomiVeicoli[position]

                        // Creiamo una nuova lista tenendo solo i parcheggi del veicolo scelto
                        val listaFiltrata = if (veicoloScelto == "Tutti i veicoli") {
                            listaCompletaParcheggi
                        } else {
                            listaCompletaParcheggi.filter { it.veicoloNome == veicoloScelto }
                        }

                        // Aggiorniamo contemporaneamente Grafico e Mappa solo con i dati filtrati!
                        impostaGrafico(listaFiltrata)
                        disegnaHeatmap(listaFiltrata)

                        // Chiamiamo la funzione passandogli la stessa lista filtrata!
                        aggiornaCardParkingEffort(listaFiltrata)
                    }

                    override fun onNothingSelected(parent: AdapterView<*>?) {}
                }
            }
        }

        // Ascolta i cambi di selezione dello Spinner
        binding.spinnerTimeFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                aggiornaStatistiche(position)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun aggiornaStatistiche(tipoFiltro: Int) {
        if (listaCompletaParcheggi.isEmpty()) {
            svuotaGrafica()
            return
        }

        val oraCorrente = System.currentTimeMillis()
        val limiteTempo = Calendar.getInstance()

        val timestampLimite: Long = when (tipoFiltro) {
            1 -> { limiteTempo.add(Calendar.MONTH, -1); limiteTempo.timeInMillis }
            2 -> { limiteTempo.add(Calendar.MONTH, -6); limiteTempo.timeInMillis }
            3 -> { limiteTempo.add(Calendar.YEAR, -1); limiteTempo.timeInMillis }
            else -> 0L
        }

        // Mostriamo tutta la lista per far compilare subito l'app.
        val listaFiltrata = listaCompletaParcheggi.filter { it.startTimeStamp >= timestampLimite }

        if (listaFiltrata.isEmpty()) {
            svuotaGrafica()
            return
        }

        // --- CALCOLO 1: TOTALE PARCHEGGI (Assoluti vs Storico) ---
        val totaliAssoluti = listaFiltrata.size // Conta TUTTI i parcheggi nel database

        // Conta solo quelli dove archiviato è uguale a false (quindi ancora vivi nello storico)
        val visibiliInStorico = listaFiltrata.count { !it.archiviato }

        // Stampiamo i due testi separati nelle rispettive TextView
        binding.tvTotalParkings.text = "Eseguiti in totale: $totaliAssoluti"
        binding.tvHistoryParkings.text = "Visibili nello storico: $visibiliInStorico"

        // --- CALCOLO 2: VEICOLO PREFERITO (CON GESTIONE PARI MERITO) ---
        val raggruppatoVeicoli = listaFiltrata.groupBy { it.veicoloNome }
        val maxSelezioniVeicolo = raggruppatoVeicoli.maxOfOrNull { it.value.size } ?: 0

        val veicoliPreferiti = raggruppatoVeicoli
            .filter { it.value.size == maxSelezioniVeicolo }
            .keys.joinToString(" / ")

        binding.tvMostUsedVehicle.text = veicoliPreferiti

        // --- CALCOLO 3: TIPO SOSTA FREQUENTE (CON GESTIONE PARI MERITO) ---
        val raggruppatoTipi = listaFiltrata.groupBy { it.tipoParcheggio }
        val maxSelezioniTipo = raggruppatoTipi.maxOfOrNull { it.value.size } ?: 0

        val tipiPreferiti = raggruppatoTipi
            .filter { it.value.size == maxSelezioniTipo }
            .keys.joinToString(" / ")

        binding.tvMostFrequentType.text = tipiPreferiti
    }

    private fun svuotaGrafica() {
        binding.tvHistoryParkings.text = "Visibili nello storico: 0"
        binding.tvMostUsedVehicle.text = "Nessun dato"
        binding.tvMostFrequentType.text = "Nessun dato"

        // Resettiamo la card se il database è completamente vuoto
        binding.tvParkingScoreValue.text = "-- / 100"
        binding.tvParkingScoreComment.text = "Inizia a viaggiare per calcolare il tuo punteggio!"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun impostaGrafico(listaParcheggi: List<SessioneParcheggio>) {
        val entries = ArrayList<BarEntry>()
        val mesi = arrayOf("Gen", "Feb", "Mar", "Apr", "Mag", "Giu", "Lug", "Ago", "Set", "Ott", "Nov", "Dic")

        // 1. Raggruppiamo i parcheggi per mese (0 = Gennaio, 11 = Dicembre)
        val raggruppatiPerMese = listaParcheggi.groupBy { sosta ->
            val cal = Calendar.getInstance()
            cal.timeInMillis = sosta.startTimeStamp
            cal.get(Calendar.MONTH)
        }

        // 2. Creiamo i punti del grafico (BarEntry) per tutti e 12 i mesi
        for (i in 0..11) {
            val numeroSoste = raggruppatiPerMese[i]?.size ?: 0
            entries.add(BarEntry(i.toFloat(), numeroSoste.toFloat()))
        }

        // 3. Impacchettiamo i dati (DataSet)
        val dataSet = BarDataSet(entries, "Numero di soste")
        dataSet.color = android.graphics.Color.parseColor("#9C27B0")
        dataSet.valueTextSize = 12f

        // 4. Consegniamo i dati al grafico
        binding.barChartSoste.data = BarData(dataSet)

        // --- ABBELLIAMO IL GRAFICO ---
        val xAxis = binding.barChartSoste.xAxis
        xAxis.valueFormatter = IndexAxisValueFormatter(mesi) // Mettiamo i nomi dei mesi!
        xAxis.position = XAxis.XAxisPosition.BOTTOM // Testo in basso
        xAxis.setDrawGridLines(false) // Via la griglia verticale brutta
        xAxis.granularity = 1f // Evita che i mesi si sovrappongano

        binding.barChartSoste.description.isEnabled = false // Togliamo la scritta "Description" di default
        binding.barChartSoste.axisRight.isEnabled = false // Togliamo l'asse Y di destra (ne basta uno a sinistra)

        // 5. [FONDAMENTALE] Diciamo al grafico di ridisegnarsi!
        binding.barChartSoste.invalidate()
    }

    private fun disegnaHeatmap(listaParcheggi: List<SessioneParcheggio>) {
        binding.mapViewStats.overlays.removeAll { it is Polygon && it.id == "heatmap_circle" }

        var ultimoPunto: GeoPoint? = null

        for (sosta in listaParcheggi) {
            ultimoPunto = GeoPoint(sosta.latitudine, sosta.longitudine)

            val cerchio = Polygon(binding.mapViewStats).apply {
                id = "heatmap_circle"
                // [MODIFICATO] Raggio più piccolo: 50 metri invece di 150
                points = Polygon.pointsAsCircle(ultimoPunto, 200.0)

                // [MODIFICATO] Colore rosso leggermente più opaco (Hex: 30)
                fillColor = android.graphics.Color.parseColor("#30FF0000")
                strokeColor = android.graphics.Color.TRANSPARENT
                strokeWidth = 0f
            }
            binding.mapViewStats.overlays.add(cerchio)
        }

        // Se c'è almeno un parcheggio, centriamo la mappa su quello
        if (ultimoPunto != null) {
            binding.mapViewStats.controller.setCenter(ultimoPunto)
        }

        binding.mapViewStats.invalidate()
    }

    // Calcola l'effort score medio su base 100 e aggiorna la vostra card personalizzata
    private fun aggiornaCardParkingEffort(listaParcheggi: List<SessioneParcheggio>) {
        // 1. Consideriamo solo i parcheggi rilevati automaticamente dai sensori
        val sessioniConScore = listaParcheggi.filter { it.parkingEffortScore != null }

        // Gestione dello stato vuoto (se l'app è appena stata installata)
        if (sessioniConScore.isEmpty()) {
            binding.tvParkingScoreValue.text = "-- / 100"
            binding.tvParkingScoreComment.text = "Inizia a viaggiare per calcolare il tuo punteggio!"
            return
        }

        // 2. Mappiamo ogni voto del DB (1-5) nel rispettivo punteggio in centesimi e facciamo la somma
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

        // 3. Calcoliamo la media matematica reale
        val mediaPunteggio = sommaPunteggiCentesimi.toDouble() / sessioniConScore.size
        val mediaArrotondata = Math.round(mediaPunteggio).toInt()

        // 4. Aggiorniamo la grafica con il risultato reale dinamico
        binding.tvParkingScoreValue.text = "$mediaArrotondata / 100"

        // 5. Cambiamo il complimento/commento in base a quanto l'utente è stato bravo!
        val commentoDinamico = when {
            mediaArrotondata >= 85 -> "Bravissimo! Sei un mago del parcheggio. 🧙‍♂️"
            mediaArrotondata >= 65 -> "Buono! Trovi parcheggio senza troppi sforzi. 👍"
            mediaArrotondata >= 45 -> "Sforzo medio. Te la cavi abbastanza bene in città. 🏙️"
            else -> "Che fatica! Giri un po' troppo prima di fermarti. 🚗💨"
        }
        binding.tvParkingScoreComment.text = commentoDinamico
    }

}