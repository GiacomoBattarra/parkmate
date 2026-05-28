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

        // Ascolta il database dei parcheggi
        viewModel.statisticheGlobaliParcheggi.observe(viewLifecycleOwner) { listaParcheggi ->
            if (listaParcheggi != null) {
                listaCompletaParcheggi = listaParcheggi
                aggiornaStatistiche(binding.spinnerTimeFilter.selectedItemPosition)
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
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}