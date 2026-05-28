package it.unibo.lam2026.parkmate.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.viewModels
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
    private val viewModel: ParcheggioViewModel by viewModels()
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
                // Chiediamo ad Android di trovare massimo 1 indirizzo per queste coordinate
                val indirizzi = geocoder.getFromLocation(latReale, lonReale, 1)

                withContext(Dispatchers.Main) {
                    if (!indirizzi.isNullOrEmpty()) {
                        // Se lo trova, prendiamo la riga intera (es. "Via Roma 15, Bologna, Italia")
                        val indirizzoTrovato = indirizzi[0].getAddressLine(0)
                        binding.tvAddress.text = "📍 $indirizzoTrovato"
                    } else {
                        // Se non ci sono strade mappate in quel punto esatto
                        binding.tvAddress.text = "📍 Indirizzo sconosciuto"
                    }
                }
            } catch (e: Exception) {
                // Se non c'è internet o il servizio fallisce, mostriamo le coordinate
                withContext(Dispatchers.Main) {
                    binding.tvAddress.text = "📍 Lat: $latReale, Lon: $lonReale"
                }
            }
        }

        val dao = AppDatabase.getDatabase(requireContext()).veicoloDao()
        val repository = VeicoloRepository(dao)

        // 2. Inizializziamo il ViewModel
        val factory = VeicoliViewModelFactory(repository)
        veicoliViewModel = ViewModelProvider(this, factory)[VeicoliViewModel::class.java]

        // Osserviamo i veicoli reali dal database
        veicoliViewModel.listaVeicoli.observe(viewLifecycleOwner) { listaReale ->

            if (listaReale.isEmpty()) {
                // CASO A: L'utente non ha ancora creato nessun veicolo
                val adapterVuoto = android.widget.ArrayAdapter(
                    requireContext(),
                    android.R.layout.simple_spinner_item,
                    listOf("Nessun veicolo! Creane uno prima.")
                )
                binding.spinnerVehicles.adapter = adapterVuoto

                // Disabilitiamo il pulsante di conferma per evitare crash
                binding.btnConfirmPark.isEnabled = false

            } else {
                // CASO B: Ci sono veicoli reali!

                // Trasformiamo la lista di oggetti "Veicolo" in una lista di testi (es. "Punto - Auto")
                val nomiVeicoli = listaReale.map { "${it.nome} (${it.tipo})" }

                val adapter = android.widget.ArrayAdapter(
                    requireContext(),
                    android.R.layout.simple_spinner_item,
                    nomiVeicoli
                )
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                binding.spinnerVehicles.adapter = adapter

                // Riabilitiamo il pulsante
                binding.btnConfirmPark.isEnabled = true
            }
        }

        veicoliViewModel.caricaVeicoli()

        // --- Logic for the Confirm Button ---
        binding.btnConfirmPark.setOnClickListener {

            // 1. IL SALVAVITA ANTI-CRASH: Usiamo "?." e "?:" per evitare che sia null
            val selectedVehicle = binding.spinnerVehicles.selectedItem?.toString() ?: ""

            // 2. CONTROLLO DI SICUREZZA: Impediamo di procedere se non ci sono veicoli
            if (selectedVehicle.isEmpty() || selectedVehicle.contains("Nessun veicolo")) {
                Toast.makeText(requireContext(), "Devi prima creare un veicolo nella schermata Veicoli!", Toast.LENGTH_LONG).show()
                return@setOnClickListener // Blocca l'esecuzione qui, niente crash!
            }

            // Determine which parking type was selected
            val parkingType = when (binding.radioGroupParkType.checkedRadioButtonId) {
                binding.radioFree.id -> "Libero"
                binding.radioHourly.id -> "A Pagamento (Orario)"
                binding.radioFixed.id -> "Ticket Fisso"
                else -> "Nessuno"
            }

            // Simple validation: make sure they selected a type
            if (parkingType == "Nessuno") {
                Toast.makeText(requireContext(), "Seleziona un tipo di sosta!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            viewModel.salvaParcheggio(selectedVehicle, parkingType, latReale, lonReale)

            Toast.makeText(requireContext(), "Parcheggio salvato a Lat: $latReale", Toast.LENGTH_SHORT).show()
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}