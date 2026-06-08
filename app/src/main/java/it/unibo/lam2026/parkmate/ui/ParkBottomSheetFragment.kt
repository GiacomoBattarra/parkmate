package it.unibo.lam2026.parkmate.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
// Condivisione dello scope del ViewModel con l'Activity host per la sincronizzazione dei dati con la mappa
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
import it.unibo.lam2026.parkmate.utils.PedestrianTrackingService

class ParkBottomSheetFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentParkBottomSheetBinding? = null
    private val binding get() = _binding!!

    // Utilizzo di activityViewModels per garantire la sincronizzazione istantanea dello stato con MapFragment
    private val viewModel: ParcheggioViewModel by activityViewModels()

    // Gestione dello stato e dell'URI per l'acquisizione di evidenze fotografiche
    private var fotoUri: android.net.Uri? = null
    private var percorsoFotoAssoluto: String? = null

    // Inizializzazione dell'Activity Result Launcher per l'app fotocamera
    private val scattaFotoLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.TakePicture()) { successo ->
        if (successo) {
            // Rendering dell'anteprima dell'immagine acquisita
            binding.imgAnteprimaFoto.setImageURI(fotoUri)
            binding.imgAnteprimaFoto.visibility = View.VISIBLE
        } else {
            // Reset del percorso in caso di annullamento dell'operazione
            percorsoFotoAssoluto = null
        }
    }
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

        // Estrazione delle coordinate correnti passate tramite bundle
        val latReale = arguments?.getDouble("LATITUDINE") ?: 0.0
        val lonReale = arguments?.getDouble("LONGITUDINE") ?: 0.0

        // Caching locale delle sessioni attive per la validazione di eventuali duplicati
        var parcheggiInCorso: List<it.unibo.lam2026.parkmate.model.SessioneParcheggio> = emptyList()
        viewModel.parcheggiAttivi.observe(viewLifecycleOwner) { lista ->
            parcheggiInCorso = lista
        }

        // Esecuzione asincrona del Reverse Geocoding per non bloccare l'interfaccia utente
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
                // Fallback sul Main thread in caso di indisponibilità dei servizi di geocoding (es. assenza di rete)
                withContext(Dispatchers.Main) {
                    // Formattazione delle coordinate a 4 decimali per una visualizzazione pulita nell'interfaccia
                    val latCorta = String.format(java.util.Locale.getDefault(), "%.4f", latReale)
                    val lonCorta = String.format(java.util.Locale.getDefault(), "%.4f", lonReale)

                    binding.tvAddress.text = "📍 Non riesco a caricare l'indirizzo (Coord: $latCorta, $lonCorta)"
                }
            }
        }

        // Inizializzazione repository e caricamento lista veicoli per lo spinner
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

        // Gestione dinamica della visibilità dei campi tariffari in base alla tipologia di sosta selezionata
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

        // Elaborazione, validazione e salvataggio dei dati relativi alla nuova sessione di parcheggio
        binding.btnConfirmPark.setOnClickListener {
            val selectedVehicle = binding.spinnerVehicles.selectedItem?.toString() ?: ""

            if (selectedVehicle.isEmpty() || selectedVehicle.contains("Nessun veicolo")) {
                Toast.makeText(requireContext(), "Devi prima creare un veicolo nella schermata Veicoli!", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            var parkingType = "Gratis"
            var tariffaFinale = 0.0
            var scadenzaStimata: Long? = null

            // Parsing e validazione dei parametri tariffari in base alla modalità scelta
            when (binding.radioGroupParkType.checkedRadioButtonId) {
                binding.radioFree.id -> {
                    parkingType = "Gratis"
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
                        scadenzaStimata = System.currentTimeMillis() + (minuti * 60 * 1000)
                    } else {
                        Toast.makeText(requireContext(), "Inserisci una durata valida in minuti!", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                }
                else -> {
                    Toast.makeText(requireContext(), "Seleziona un tipo di sosta!", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
            }

            // Verifica l'esistenza di conflitti di stato (es. veicolo già in sosta)
            val isAlreadyParked = parcheggiInCorso.any { it.veicoloNome == selectedVehicle }

            // Lambda che incapsula la logica di persistenza e l'avvio del tracking pedonale
            val eseguiSalvataggio = {
                val notaInserita = binding.etNotaParcheggio.text.toString().takeIf { it.isNotBlank() }

                viewModel.salvaParcheggio(
                    nomeVeicolo = selectedVehicle,
                    tipo = parkingType,
                    lat = latReale,
                    lon = lonReale,
                    tariffa = tariffaFinale,
                    scadenzaTimestamp = scadenzaStimata,
                    nota = notaInserita,
                    fotoPath = percorsoFotoAssoluto
                )

                // Avvio del servizio in foreground per il calcolo della distanza a piedi
                val serviceIntent = android.content.Intent(requireContext(), PedestrianTrackingService::class.java)
                androidx.core.content.ContextCompat.startForegroundService(requireContext(), serviceIntent)

                Toast.makeText(requireContext(), "Parcheggio iniziato!", Toast.LENGTH_SHORT).show()
                dismiss()
            }

            // Gestione dei conflitti: richiede esplicita conferma all'utente per sovrascrivere una sosta attiva
            if (isAlreadyParked) {
                com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                    .setTitle("⚠️ Veicolo già in sosta!")
                    .setMessage("Attenzione: '$selectedVehicle' risulta già parcheggiato altrove.\n\nVuoi terminare la sosta precedente e iniziarne una nuova qui?")
                    .setPositiveButton("Sì, sostituisci") { _, _ ->
                        eseguiSalvataggio()
                    }
                    .setNegativeButton("No, annulla") { dialog, _ ->
                        dialog.dismiss()
                    }
                    .show()
            } else {
                // Nessun conflitto rilevato, procede direttamente con il salvataggio
                eseguiSalvataggio()
            }
        }

        // Preparazione del FileProvider e avvio dell'intent per la fotocamera
        binding.btnScattaFoto.setOnClickListener {
            try {
                val fileFoto = creaFileImmagine()

                // Generazione di un URI sicuro tramite FileProvider, coerente con le autorizzazioni del Manifest
                fotoUri = androidx.core.content.FileProvider.getUriForFile(
                    requireContext(),
                    "${requireContext().packageName}.fileprovider",
                    fileFoto
                )

                scattaFotoLauncher.launch(fotoUri)

            } catch (e: Exception) {
                e.printStackTrace()
                android.widget.Toast.makeText(requireContext(), "Errore nell'apertura della fotocamera", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun creaFileImmagine(): java.io.File {
        // Generazione di un nome file univoco basato sul timestamp corrente
        val timeStamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
        val nomeFile = "JPEG_${timeStamp}_"

        // Utilizzo della directory cache esterna (configurata in file_paths.xml)
        val cartellaStorage = requireContext().externalCacheDir

        // Creazione fisica del file temporaneo
        val fileImmagine = java.io.File.createTempFile(nomeFile, ".jpg", cartellaStorage)

        // Memorizzazione del percorso assoluto per l'inserimento nel database Room
        percorsoFotoAssoluto = fileImmagine.absolutePath

        return fileImmagine
    }
}