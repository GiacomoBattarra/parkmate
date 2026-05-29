package it.unibo.lam2026.parkmate.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
// Usiamo activityViewModels per condividere i dati con la Mappa!
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import android.location.Geocoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import it.unibo.lam2026.parkmate.databinding.FragmentParkBottomSheetBinding
import it.unibo.lam2026.parkmate.model.AppDatabase
import it.unibo.lam2026.parkmate.model.VeicoloRepository
import it.unibo.lam2026.parkmate.viewmodel.ParcheggioViewModel
import it.unibo.lam2026.parkmate.viewmodel.VeicoliViewModel
import it.unibo.lam2026.parkmate.viewmodel.VeicoliViewModelFactory

class ParkBottomSheetFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentParkBottomSheetBinding? = null
    private val binding get() = _binding!!

    // CAMBIO IMPORTANTE: Usiamo activityViewModels così Mappa e BottomSheet comunicano istantaneamente
    private val viewModel: ParcheggioViewModel by activityViewModels()

    private lateinit var veicoliViewModel: VeicoliViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentParkBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. Estraiamo SUBITO le coordinate
        val latReale = arguments?.getDouble("LATITUDINE") ?: 0.0
        val lonReale = arguments?.getDouble("LONGITUDINE") ?: 0.0

        // 2. REVERSE GEOCODING (Traduzione Coordinate -> Indirizzo) in Background
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val geocoder = Geocoder(requireContext(), Locale.getDefault())
                val indirizzi = geocoder.getFromLocation(latReale, lonReale, 1)

                withContext(Dispatchers.Main) {
                    if (!indirizzi.isNullOrEmpty()) {
                        val indirizzoTrovato = indirizzi[0].getAddressLine(0)
                        binding.tvAddress.text = "📍 $indirizzoTrovato"
                    } else {
                        binding.tvAddress.text = "📍 Indirizzo sconosciuto"
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.tvAddress.text = "📍 Lat: $latReale, Lon: $lonReale"
                }
            }
        }

        // 3. CARICAMENTO VEICOLI
        val dao = AppDatabase.getDatabase(requireContext()).veicoloDao()
        val repository = VeicoloRepository(dao)
        val factory = VeicoliViewModelFactory(repository)
        veicoliViewModel = ViewModelProvider(this, factory)[VeicoliViewModel::class.java]

        veicoliViewModel.listaVeicoli.observe(viewLifecycleOwner) { listaReale ->
            if (listaReale.isEmpty()) {
                val adapterVuoto = ArrayAdapter(
                    requireContext(),
                    android.R.layout.simple_spinner_item,
                    listOf("Nessun veicolo! Creane uno prima.")
                )
                binding.spinnerVehicles.adapter = adapterVuoto
                binding.btnConfirmPark.isEnabled = false
            } else {
                val nomiVeicoli = listaReale.map { "${it.nome} (${it.tipo})" }
                val adapter = ArrayAdapter(
                    requireContext(),
                    android.R.layout.simple_spinner_item,
                    nomiVeicoli
                )
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                binding.spinnerVehicles.adapter = adapter
                binding.btnConfirmPark.isEnabled = true
            }
        }
        veicoliViewModel.caricaVeicoli()

        // -------------------------------------------------------------------------
        // [NUOVO] 4. DINAMISMO UI: Mostra o nascondi i campi tariffe al click
        // -------------------------------------------------------------------------
        binding.radioGroupParkType.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                binding.radioFree.id -> {
                    binding.layoutHourly.visibility = View.GONE
                    binding.layoutFixed.visibility = View.GONE
                }
                binding.radioHourly.id -> {
                    binding.layoutHourly.visibility = View.VISIBLE
                    binding.layoutFixed.visibility = View.GONE
                }
                binding.radioFixed.id -> {
                    binding.layoutHourly.visibility = View.GONE
                    binding.layoutFixed.visibility = View.VISIBLE
                }
            }
        }

        // -------------------------------------------------------------------------
        // 5. SALVATAGGIO DEI DATI
        // -------------------------------------------------------------------------
        binding.btnConfirmPark.setOnClickListener {
            val selectedVehicle = binding.spinnerVehicles.selectedItem?.toString() ?: ""

            if (selectedVehicle.isEmpty() || selectedVehicle.contains("Nessun veicolo")) {
                Toast.makeText(requireContext(), "Devi prima creare un veicolo nella schermata Veicoli!", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            var parkingType = "Libero"
            var tariffaFinale = 0.0
            var scadenzaStimata: Long? = null

            // Analizziamo cosa ha scelto l'utente e leggiamo i numeri
            when (binding.radioGroupParkType.checkedRadioButtonId) {

                binding.radioFree.id -> {
                    parkingType = "Libero"
                }

                binding.radioHourly.id -> {
                    parkingType = "A pagamento (Orario)"
                    val inputTariffa = binding.etHourlyTariff.text.toString()
                    tariffaFinale = if (inputTariffa.isNotEmpty()) inputTariffa.toDouble() else 0.0
                }

                binding.radioFixed.id -> {
                    parkingType = "Ticket Fisso"
                    val inputCosto = binding.etFixedCost.text.toString()
                    tariffaFinale = if (inputCosto.isNotEmpty()) inputCosto.toDouble() else 0.0

                    val inputMinuti = binding.etFixedDuration.text.toString()
                    val minuti = if (inputMinuti.isNotEmpty()) inputMinuti.toLong() else 0L

                    if (minuti > 0) {
                        // Calcolo timestamp: ORA + (minuti scelti in millisecondi)
                        scadenzaStimata = System.currentTimeMillis() + (minuti * 60 * 1000)
                    } else {
                        Toast.makeText(requireContext(), "Inserisci una durata valida in minuti!", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener // Blocca tutto se non mette i minuti!
                    }
                }
                else -> {
                    Toast.makeText(requireContext(), "Seleziona un tipo di sosta!", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
            }

            // Chiamata finale al ViewModel con i NUOVI parametri
            viewModel.salvaParcheggio(
                nomeVeicolo = selectedVehicle,
                tipo = parkingType,
                lat = latReale,
                lon = lonReale,
                tariffa = tariffaFinale,
                scadenzaTimestamp = scadenzaStimata
            )

            Toast.makeText(requireContext(), "Parcheggio iniziato!", Toast.LENGTH_SHORT).show()
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}