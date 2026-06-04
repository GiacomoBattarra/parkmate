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

    // 2. Usiamo la classe corretta: SessioneParcheggio
    private var listaCompletaParcheggi: List<SessioneParcheggio> = emptyList()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // IMPORTANTISSIMO: Inizializza la configurazione di osmdroid con il contesto dell'app
        Configuration.getInstance().load(
            requireContext(),
            androidx.preference.PreferenceManager.getDefaultSharedPreferences(requireContext())
        )
    }
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
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
        // 1. Forziamo esplicitamente la sorgente delle mappe (Standard)
        binding.mapViewStats.setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK)
        binding.mapViewStats.setMultiTouchControls(true)
        binding.mapViewStats.controller.setZoom(15.0)

        // 2. Impostiamo un centro di default (es. Bologna)
        // Se non lo facciamo, prima di scaricare i dati la mappa parte in mezzo all'oceano (sfondo grigio!)
        binding.mapViewStats.controller.setCenter(org.osmdroid.util.GeoPoint(44.4949, 11.3426))

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
                        // disegnaHeatmap(listaFiltrata)
                        disegnaMappaSforzoDinamica(listaFiltrata)

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

    // Funzione per ottenere la chiave della cella (griglia 200m x 200m)
    private fun getGridKey(lat: Double, lon: Double, cellSizeDeg: Double = 0.002): String {
        val gridLat = (lat / cellSizeDeg).toInt()
        val gridLon = (lon / cellSizeDeg).toInt()
        return "$gridLat,$gridLon"
    }

    // Calcola lo score medio per ogni cella a partire dalla lista dei parcheggi
    private fun calcolaScoreMedioPerCella(lista: List<SessioneParcheggio>): Map<String, Double> {
        val perCella = mutableMapOf<String, MutableList<Int>>()
        for (sessione in lista) {
            val score = sessione.parkingEffortScore ?: continue
            val key = getGridKey(sessione.latitudine, sessione.longitudine)
            perCella.getOrPut(key) { mutableListOf() }.add(score)
        }
        return perCella.mapValues { (_, scores) -> scores.average() }
    }

    // Restituisce il colore in base allo score medio (verde = score basso, rosso = score alto)
    private fun colorePerScore(avgScore: Double): Int {
        val ratio = ((avgScore - 1) / 4.0).coerceIn(0.0, 1.0)
        val red = (255 * ratio).toInt()
        val green = (255 * (1 - ratio)).toInt()
        return Color.argb(180, red, green, 0) // semi-trasparente
    }

    private fun disegnaMappaSforzoDinamica(listaParcheggi: List<SessioneParcheggio>) {
        // 1. Puliamo i vecchi disegni
        binding.mapViewStats.overlays.removeAll { it is org.osmdroid.views.overlay.Polygon && it.id == "effort_circle" }

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

            // 3. Scegliamo il colore in base allo sforzo (1 = Verde/Ottimo, 5 = Rosso/Pessimo)
            // Usiamo "40" all'inizio dell'esadecimale per dare un'opacità del 25% circa.
            // Quando i cerchi si sovrappongono, il colore diventerà più intenso!
            val coloreHex = when(score) {
                1 -> "#404CAF50" // Verde
                2 -> "#408BC34A" // Verde chiaro
                3 -> "#40FFEB3B" // Giallo
                4 -> "#40FF9800" // Arancione
                5 -> "#40F44336" // Rosso
                else -> "#409E9E9E" // Grigio default
            }

            val cerchio = org.osmdroid.views.overlay.Polygon(binding.mapViewStats).apply {
                id = "effort_circle"
                // Creiamo un cerchio di 150 metri di raggio
                points = org.osmdroid.views.overlay.Polygon.pointsAsCircle(ultimoCentro, 150.0)
                fillColor = android.graphics.Color.parseColor(coloreHex)
                strokeColor = android.graphics.Color.TRANSPARENT
                strokeWidth = 0f
            }

            binding.mapViewStats.overlays.add(cerchio)
        }

        // Centriamo la mappa sull'ultimo parcheggio registrato
        if (ultimoCentro != null) {
            binding.mapViewStats.controller.animateTo(ultimoCentro)
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

    override fun onResume() {
        super.onResume()
        // Sveglia la mappa e forza il caricamento dei tasselli grafici
        binding.mapViewStats.onResume()
    }

    override fun onPause() {
        super.onPause()
        // Mette in pausa il download per evitare memory leak
        binding.mapViewStats.onPause()
    }

}