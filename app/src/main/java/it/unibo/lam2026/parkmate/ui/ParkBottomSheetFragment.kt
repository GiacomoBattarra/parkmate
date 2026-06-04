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
import it.unibo.lam2026.parkmate.utils.PedestrianTrackingService

class ParkBottomSheetFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentParkBottomSheetBinding? = null
    private val binding get() = _binding!!

    // CAMBIO IMPORTANTE: Usiamo activityViewModels così Mappa e BottomSheet comunicano istantaneamente
    private val viewModel: ParcheggioViewModel by activityViewModels()
    // --- VARIABILI PER LA FOTO ---
    private var fotoUri: android.net.Uri? = null
    private var percorsoFotoAssoluto: String? = null

    // Preparo il "Lanciatore" che aspetta il risultato della fotocamera
    private val scattaFotoLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.TakePicture()) { successo ->
        if (successo) {
            // La foto è stata scattata! Mostriamo l'anteprima nel quadratino
            binding.imgAnteprimaFoto.setImageURI(fotoUri)
            binding.imgAnteprimaFoto.visibility = View.VISIBLE
        } else {
            // L'utente ha chiuso la fotocamera senza scattare
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

        // 1. Estraiamo SUBITO le coordinate
        val latReale = arguments?.getDouble("LATITUDINE") ?: 0.0
        val lonReale = arguments?.getDouble("LONGITUDINE") ?: 0.0

        // --- NUOVO: Teniamo in memoria i parcheggi attivi per controllare i doppioni! ---
        var parcheggiInCorso: List<it.unibo.lam2026.parkmate.model.SessioneParcheggio> = emptyList()
        viewModel.parcheggiAttivi.observe(viewLifecycleOwner) { lista ->
            parcheggiInCorso = lista
        }

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
                // Ritorno sul thread principale per aggiornare la grafica
                withContext(Dispatchers.Main) {
                    // Arrotondiamo le coordinate a 4 decimali per non avere una sbrodolata di numeri sullo schermo
                    val latCorta = String.format(java.util.Locale.getDefault(), "%.4f", latReale)
                    val lonCorta = String.format(java.util.Locale.getDefault(), "%.4f", lonReale)

                    // Mostriamo il messaggio amichevole con le coordinate!
                    binding.tvAddress.text = "📍 Non riesco a caricare l'indirizzo (Coord: $latCorta, $lonCorta)"
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
        // 5. SALVATAGGIO DEI DATI (Con avviso di sovrascrittura!)
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

            // --- NUOVA LOGICA: Controllo Sovrascrittura ---

            // 1. Controlliamo se il veicolo è già parcheggiato guardando la nostra lista aggiornata
            val isAlreadyParked = parcheggiInCorso.any { it.veicoloNome == selectedVehicle }

            // 2. Creiamo una "mini-funzione" con il salvataggio vero e proprio
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
                // AVVIO IL MOTORE DI TRACCIAMENTO CAMMINATA (Spostato qui dentro!)
                val serviceIntent = android.content.Intent(requireContext(), PedestrianTrackingService::class.java)
                androidx.core.content.ContextCompat.startForegroundService(requireContext(), serviceIntent)

                Toast.makeText(requireContext(), "Parcheggio iniziato!", Toast.LENGTH_SHORT).show()
                dismiss() // Chiude il BottomSheet
            }

            // 3. Decidiamo cosa fare
            if (isAlreadyParked) {
                // L'auto è già parcheggiata: mostriamo il pop-up di conferma!
                com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                    .setTitle("⚠️ Veicolo già in sosta!")
                    .setMessage("Attenzione: '$selectedVehicle' risulta già parcheggiato altrove.\n\nVuoi terminare la sosta precedente e iniziarne una nuova qui?")
                    .setPositiveButton("Sì, sostituisci") { _, _ ->
                        eseguiSalvataggio() // L'utente conferma, andiamo avanti
                    }
                    .setNegativeButton("No, annulla") { dialog, _ ->
                        dialog.dismiss() // L'utente rifiuta, chiudiamo solo l'avviso e non facciamo nulla!
                    }
                    .show()
            } else {
                // L'auto è libera: salviamo direttamente senza fastidiosi pop-up
                eseguiSalvataggio()
            }
        }

        // --- CLICK SUL BOTTONE FOTOCAMERA ---
        binding.btnScattaFoto.setOnClickListener {
            try {
                // 1. Creiamo il file vuoto
                val fileFoto = creaFileImmagine()

                // 2. Generiamo l'URI sicuro tramite il FileProvider (DEVE combaciare con il Manifest)
                fotoUri = androidx.core.content.FileProvider.getUriForFile(
                    requireContext(),
                    "${requireContext().packageName}.fileprovider",
                    fileFoto
                )

                // 3. Lanciamo la fotocamera passandole l'URI sicuro!
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
        // Creiamo un nome unico basato sulla data e ora attuale
        val timeStamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
        val nomeFile = "JPEG_${timeStamp}_"

        // Usiamo la cartella Cache sicura (quella che abbiamo autorizzato nel file_paths.xml)
        val cartellaStorage = requireContext().externalCacheDir

        // Creiamo il file fisico
        val fileImmagine = java.io.File.createTempFile(nomeFile, ".jpg", cartellaStorage)

        // Salviamo il percorso assoluto da mandare al Database!
        percorsoFotoAssoluto = fileImmagine.absolutePath

        return fileImmagine
    }


}