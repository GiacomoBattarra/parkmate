package it.unibo.lam2026.parkmate.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import it.unibo.lam2026.parkmate.databinding.FragmentMapBinding
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import android.location.Geocoder
import androidx.lifecycle.lifecycleScope
import it.unibo.lam2026.parkmate.R
import it.unibo.lam2026.parkmate.model.AppDatabase
import it.unibo.lam2026.parkmate.model.ParcheggioRepository
import it.unibo.lam2026.parkmate.model.PosizioneSalvata
import it.unibo.lam2026.parkmate.utils.ParkingAlarmReceiver
import it.unibo.lam2026.parkmate.utils.PedestrianTrackingService
import it.unibo.lam2026.parkmate.viewmodel.MainViewModel
import it.unibo.lam2026.parkmate.viewmodel.MainViewModelFactory
import it.unibo.lam2026.parkmate.viewmodel.PosizioniSalvateViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import org.osmdroid.views.overlay.Polygon

class MapFragment : Fragment() {

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!
    private lateinit var myLocationOverlay: MyLocationNewOverlay

    // Gestione dello stato dell'interfaccia utente
    private var isSelectingLocation = false
    private var isSelectingFavorite = false

    // Gestione dello stato di modifica per le posizioni salvate
    private var isEditingFavorite = false
    private var locationBeingEdited: PosizioneSalvata? = null

    // Flag per la modalità "Mappa Pulita" (Zen Mode)
    private var isMappaPulita = false

    private lateinit var viewModel: MainViewModel
    private val posizioniViewModel: PosizioniSalvateViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) setupMyLocation()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().load(
            requireContext(),
            PreferenceManager.getDefaultSharedPreferences(requireContext())
        )
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val dao = AppDatabase.getDatabase(requireContext()).parcheggioDao()
        val repository = ParcheggioRepository(dao)
        val factory = MainViewModelFactory(repository)
        viewModel = ViewModelProvider(this, factory)[MainViewModel::class.java]

        setupMap()
        checkLocationPermissions()

        // Rendering e monitoraggio dei parcheggi attualmente attivi
        viewModel.listaParcheggi.observe(viewLifecycleOwner) { lista ->
            binding.mapView.overlays.removeAll { it is org.osmdroid.views.overlay.Marker && it.id == "PARCHEGGIO" }

            if (!lista.isNullOrEmpty()) {
                val sostaAttiva = lista[0]
                binding.btnTerminaSosta.visibility = View.VISIBLE

                for (parcheggio in lista) {
                    val segnaposto = org.osmdroid.views.overlay.Marker(binding.mapView)
                    segnaposto.id = "PARCHEGGIO"
                    segnaposto.position = org.osmdroid.util.GeoPoint(parcheggio.latitudine, parcheggio.longitudine)

                    val iconaMezzo = when {
                        parcheggio.veicoloNome.contains("Moto", ignoreCase = true) -> "🏍️"
                        parcheggio.veicoloNome.contains("Bici", ignoreCase = true) -> "🚲"
                        else -> "🚗"
                    }
                    segnaposto.title = "$iconaMezzo ${parcheggio.veicoloNome}"

                    // Calcolo dinamico di tempistiche e costi basato sulle tariffe
                    val formattaData = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                    val oraInizio = formattaData.format(java.util.Date(parcheggio.startTimeStamp))

                    val tempoAttuale = System.currentTimeMillis()
                    val elapsedMillis = tempoAttuale - parcheggio.startTimeStamp
                    val ore = java.util.concurrent.TimeUnit.MILLISECONDS.toHours(elapsedMillis)
                    val minuti = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(elapsedMillis) % 60
                    val tempoTrascorso = "${ore}h ${minuti}m"

                    var costoTesto = "Gratis"
                    if (parcheggio.tariffa > 0.0) {
                        if (parcheggio.tipoParcheggio.contains("Fiss", ignoreCase = true)) {
                            costoTesto = String.format(java.util.Locale.getDefault(), "€%.2f (Fisso)", parcheggio.tariffa)
                        } else {
                            val oreDecimali = elapsedMillis.toDouble() / (1000.0 * 60.0 * 60.0)
                            val costoCalc = oreDecimali * parcheggio.tariffa
                            costoTesto = String.format(java.util.Locale.getDefault(), "€%.2f", costoCalc)
                        }
                    }

                    val infoDettagliate = "${parcheggio.tipoParcheggio}\n🕒 Inizio: $oraInizio | ⏳ Trascorso: $tempoTrascorso\n💰 Costo Attuale: $costoTesto"
                    segnaposto.snippet = infoDettagliate

                    // Mantiene la coerenza visiva se la modalità "Mappa Pulita" è attiva
                    if (isMappaPulita) segnaposto.setAlpha(0.0f)

                    segnaposto.relatedObject = parcheggio.id
                    segnaposto.infoWindow = ParkInfoWindow(binding.mapView) { idSessione ->
                        viewModel.terminaParcheggio(idSessione)

                        // Interrompe il tracciamento pedonale in caso di terminazione manuale
                        val stopIntent = android.content.Intent(requireContext(), PedestrianTrackingService::class.java).apply {
                            action = "STOP_TRACKING"
                        }
                        requireContext().startService(stopIntent)

                        Toast.makeText(requireContext(), "Sosta terminata dalla mappa!", Toast.LENGTH_SHORT).show()
                    }

                    segnaposto.setOnMarkerClickListener { marker, mapView ->
                        if (isMappaPulita) return@setOnMarkerClickListener true

                        if (marker.isInfoWindowOpen) marker.closeInfoWindow()
                        else {
                            org.osmdroid.views.overlay.infowindow.InfoWindow.closeAllInfoWindowsOn(mapView)
                            marker.showInfoWindow()
                        }
                        true
                    }

                    // Risoluzione asincrona delle coordinate in indirizzo
                    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            val geocoder = Geocoder(requireContext(), Locale.getDefault())
                            val indirizzi = geocoder.getFromLocation(parcheggio.latitudine, parcheggio.longitudine, 1)

                            val indirizzoPulito = if (!indirizzi.isNullOrEmpty()) {
                                val addr = indirizzi[0]
                                val via = addr.thoroughfare ?: ""
                                val civico = addr.subThoroughfare ?: ""
                                val citta = addr.locality ?: ""
                                if (via.isNotEmpty() && citta.isNotEmpty()) {
                                    if (civico.isNotEmpty()) "$via $civico, $citta" else "$via, $citta"
                                } else addr.getAddressLine(0)
                            } else "Indirizzo sconosciuto"

                            withContext(Dispatchers.Main) {
                                segnaposto.snippet = "$infoDettagliate\n📍 $indirizzoPulito"
                                if (segnaposto.isInfoWindowOpen) {
                                    segnaposto.closeInfoWindow()
                                    segnaposto.showInfoWindow()
                                }
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                val latCorta = String.format(Locale.getDefault(), "%.4f", parcheggio.latitudine)
                                val lonCorta = String.format(Locale.getDefault(), "%.4f", parcheggio.longitudine)
                                segnaposto.snippet = "$infoDettagliate\n📍 Non riesco a caricare l'indirizzo\n📍 Coord: $latCorta, $lonCorta"
                                if (segnaposto.isInfoWindowOpen) {
                                    segnaposto.closeInfoWindow()
                                    segnaposto.showInfoWindow()
                                }
                            }
                        }
                    }

                    segnaposto.setAnchor(org.osmdroid.views.overlay.Marker.ANCHOR_CENTER, org.osmdroid.views.overlay.Marker.ANCHOR_BOTTOM)
                    binding.mapView.overlays.add(segnaposto)
                }
            } else {
                binding.btnTerminaSosta.visibility = View.GONE
            }
            binding.mapView.invalidate()
        }

        // Gestione rendering e interazioni per i luoghi preferiti
        val posizioniAdapter = PosizioniSalvateAdapter(
            posizioni = emptyList(),

            onPosizioneClick = { posizioneSalvata ->
                val mapController = binding.mapView.controller
                mapController.animateTo(org.osmdroid.util.GeoPoint(posizioneSalvata.latitudine, posizioneSalvata.longitudine))

                binding.recyclerPosizioniSalvate.visibility = View.GONE
                binding.headerLuoghiSalvati.text = "I tuoi luoghi salvati ▼"
            },

            onEditClick = { posizioneSalvata ->
                binding.recyclerPosizioniSalvate.visibility = View.GONE
                binding.headerLuoghiSalvati.text = "I tuoi luoghi salvati ▼"

                isEditingFavorite = true
                locationBeingEdited = posizioneSalvata

                binding.imgCenterPin.visibility = View.VISIBLE
                binding.fabAggiungiPosizione.setImageResource(android.R.drawable.ic_menu_save)

                val mapController = binding.mapView.controller
                mapController.animateTo(org.osmdroid.util.GeoPoint(posizioneSalvata.latitudine, posizioneSalvata.longitudine))

                Toast.makeText(requireContext(), "Sposta il bersaglio sulla nuova posizione e premi il pulsante blu", Toast.LENGTH_LONG).show()

                if (::myLocationOverlay.isInitialized) myLocationOverlay.disableFollowLocation()
            },

            onDeleteClick = { posizioneSalvata ->
                android.app.AlertDialog.Builder(requireContext())
                    .setTitle("Elimina luogo")
                    .setMessage("Sei sicuro di voler eliminare '${posizioneSalvata.nome}'?")
                    .setPositiveButton("Sì, elimina") { _, _ ->
                        posizioniViewModel.eliminaPosizione(posizioneSalvata)
                        Toast.makeText(requireContext(), "Eliminato: ${posizioneSalvata.nome}", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Annulla", null)
                    .show()
            }
        )

        binding.recyclerPosizioniSalvate.adapter = posizioniAdapter

        posizioniViewModel.posizioniSalvate.observe(viewLifecycleOwner) { listaAggiornata ->
            posizioniAdapter.updateData(listaAggiornata)
            binding.mapView.overlays.removeAll { it is org.osmdroid.views.overlay.Marker && it.id == "PREFERITO" }

            for (posizione in listaAggiornata) {
                val markerCuore = org.osmdroid.views.overlay.Marker(binding.mapView)

                markerCuore.setAnchor(1f, 1f)

                markerCuore.id = "PREFERITO"
                markerCuore.position = org.osmdroid.util.GeoPoint(posizione.latitudine, posizione.longitudine)

                markerCuore.title = posizione.nome
                markerCuore.snippet = "📍 Ricerca indirizzo in corso..."

                val icona = ContextCompat.getDrawable(requireContext(), R.drawable.ic_heart)
                icona?.setTint(android.graphics.Color.RED)
                markerCuore.icon = icona

                if (isMappaPulita) markerCuore.setAlpha(0.0f)

                markerCuore.setAnchor(org.osmdroid.views.overlay.Marker.ANCHOR_CENTER, org.osmdroid.views.overlay.Marker.ANCHOR_BOTTOM)

                markerCuore.setOnMarkerClickListener { marker, _ ->
                    if (isMappaPulita) return@setOnMarkerClickListener true

                    if (marker.isInfoWindowOpen) marker.closeInfoWindow() else marker.showInfoWindow()
                    true
                }

                binding.mapView.overlays.add(markerCuore)

                // Geocoding in background per l'etichetta del preferito
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val geocoder = Geocoder(requireContext(), Locale.getDefault())
                        val indirizzi = geocoder.getFromLocation(posizione.latitudine, posizione.longitudine, 1)

                        val indirizzoPulito = if (!indirizzi.isNullOrEmpty()) {
                            val addr = indirizzi[0]
                            val via = addr.thoroughfare ?: ""
                            val civico = addr.subThoroughfare ?: ""
                            val citta = addr.locality ?: ""

                            if (via.isNotEmpty() && citta.isNotEmpty()) {
                                if (civico.isNotEmpty()) "$via $civico, $citta" else "$via, $citta"
                            } else {
                                addr.getAddressLine(0) ?: "Indirizzo sconosciuto"
                            }
                        } else {
                            "Indirizzo non trovato"
                        }

                        withContext(Dispatchers.Main) {
                            markerCuore.snippet = "📍 $indirizzoPulito"

                            if (markerCuore.isInfoWindowOpen) {
                                markerCuore.closeInfoWindow()
                                markerCuore.showInfoWindow()
                            }
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            val latCorta = String.format(Locale.getDefault(), "%.4f", posizione.latitudine)
                            val lonCorta = String.format(Locale.getDefault(), "%.4f", posizione.longitudine)
                            markerCuore.snippet = "📍 Non riesco a caricare l'indirizzo\n📍 Coord: $latCorta, $lonCorta"

                            if (markerCuore.isInfoWindowOpen) {
                                markerCuore.closeInfoWindow()
                                markerCuore.showInfoWindow()
                            }
                        }
                    }
                }
            }
            binding.mapView.invalidate()
        }

        // Flusso di selezione manuale del parcheggio sulla mappa
        binding.btnParkHere.setOnClickListener {
            if (isSelectingFavorite || isEditingFavorite) return@setOnClickListener

            if (!isSelectingLocation) {
                isSelectingLocation = true
                binding.imgCenterPin.visibility = View.VISIBLE
                binding.btnParkHere.setImageResource(android.R.drawable.ic_menu_save)
                Toast.makeText(requireContext(), "Sposta la mappa e conferma la posizione", Toast.LENGTH_SHORT).show()
                if (::myLocationOverlay.isInitialized) myLocationOverlay.disableFollowLocation()
            } else {
                val centerPoint = binding.mapView.mapCenter as GeoPoint
                val bottomSheet = ParkBottomSheetFragment()
                val bundleDati = Bundle()
                bundleDati.putDouble("LATITUDINE", centerPoint.latitude)
                bundleDati.putDouble("LONGITUDINE", centerPoint.longitude)
                bottomSheet.arguments = bundleDati
                bottomSheet.show(parentFragmentManager, "ParkBottomSheet")

                isSelectingLocation = false
                binding.imgCenterPin.visibility = View.GONE
                binding.btnParkHere.setImageResource(R.drawable.ic_parking)
            }
        }

        // Gestione espansione del menu dei luoghi salvati
        var isListExpanded = false
        binding.headerLuoghiSalvati.setOnClickListener {
            isListExpanded = !isListExpanded
            if (isListExpanded) {
                binding.recyclerPosizioniSalvate.visibility = View.VISIBLE
                binding.headerLuoghiSalvati.text = "I tuoi luoghi salvati ▲"
            } else {
                binding.recyclerPosizioniSalvate.visibility = View.GONE
                binding.headerLuoghiSalvati.text = "I tuoi luoghi salvati ▼"
            }
        }

        // Flusso di aggiunta/modifica di una posizione preferita
        binding.fabAggiungiPosizione.setOnClickListener {
            if (isSelectingLocation) return@setOnClickListener

            // Fase di conferma modifica
            if (isEditingFavorite) {
                val currentGeoPoint = binding.mapView.mapCenter as GeoPoint
                isEditingFavorite = false
                binding.imgCenterPin.visibility = View.GONE
                binding.fabAggiungiPosizione.setImageResource(R.drawable.ic_heart)

                val campoTesto = android.widget.EditText(requireContext())
                campoTesto.setText(locationBeingEdited?.nome)

                android.app.AlertDialog.Builder(requireContext())
                    .setTitle("Conferma Modifica")
                    .setMessage("Modifica il nome se lo desideri:")
                    .setView(campoTesto)
                    .setPositiveButton("Aggiorna") { dialog, _ ->
                        val nomeInserito = campoTesto.text.toString()
                        if (nomeInserito.isNotBlank() && locationBeingEdited != null) {
                            val posizioneAggiornata = locationBeingEdited!!.copy(
                                nome = nomeInserito,
                                latitudine = currentGeoPoint.latitude,
                                longitudine = currentGeoPoint.longitude
                            )
                            posizioniViewModel.aggiornaPosizione(posizioneAggiornata)
                            Toast.makeText(requireContext(), "Posizione aggiornata!", Toast.LENGTH_SHORT).show()
                            locationBeingEdited = null
                            if (!isListExpanded) binding.headerLuoghiSalvati.performClick()
                        } else {
                            Toast.makeText(requireContext(), "Il nome non può essere vuoto!", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .setNegativeButton("Annulla") { dialog, _ -> locationBeingEdited = null }
                    .setOnCancelListener { locationBeingEdited = null }
                    .show()

                return@setOnClickListener
            }

            // Fase di aggiunta nuovo preferito
            if (!isSelectingFavorite) {
                isSelectingFavorite = true
                binding.imgCenterPin.visibility = View.VISIBLE
                binding.fabAggiungiPosizione.setImageResource(android.R.drawable.ic_menu_save)
                Toast.makeText(requireContext(), "Sposta il bersaglio sul luogo e premi di nuovo", Toast.LENGTH_LONG).show()

                if (::myLocationOverlay.isInitialized) myLocationOverlay.disableFollowLocation()
            } else {
                val currentGeoPoint = binding.mapView.mapCenter as GeoPoint

                isSelectingFavorite = false
                binding.imgCenterPin.visibility = View.GONE
                binding.fabAggiungiPosizione.setImageResource(R.drawable.ic_heart)

                val campoTesto = android.widget.EditText(requireContext())
                campoTesto.hint = "Es. Casa, Palestra, Lavoro..."

                android.app.AlertDialog.Builder(requireContext())
                    .setTitle("Salva questa posizione")
                    .setMessage("Inserisci un nome per ricordare questo luogo:")
                    .setView(campoTesto)
                    .setPositiveButton("Salva") { dialog, _ ->
                        val nomeInserito = campoTesto.text.toString()

                        if (nomeInserito.isNotBlank()) {
                            posizioniViewModel.salvaNuovaPosizione(nomeInserito, currentGeoPoint.latitude, currentGeoPoint.longitude)
                            Toast.makeText(requireContext(), "Salvato: $nomeInserito", Toast.LENGTH_SHORT).show()
                            if (!isListExpanded) binding.headerLuoghiSalvati.performClick()
                        } else {
                            Toast.makeText(requireContext(), "Il nome non può essere vuoto!", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .setNegativeButton("Annulla") { dialog, _ -> dialog.dismiss() }
                    .show()
            }
        }

        // Toggle per la "Mappa Pulita" (Zen Mode) che nasconde momentaneamente i marker
        binding.btnTerminaSosta.setOnClickListener {
            isMappaPulita = !isMappaPulita

            for (overlay in binding.mapView.overlays) {
                if (overlay is org.osmdroid.views.overlay.Marker) {
                    if (overlay.id == "PARCHEGGIO" || overlay.id == "PREFERITO") {
                        if (isMappaPulita) {
                            overlay.setAlpha(0.0f)
                            overlay.closeInfoWindow()
                        } else {
                            overlay.setAlpha(1.0f)
                        }
                    }
                }
            }

            if (isMappaPulita) {
                Toast.makeText(requireContext(), "Mappa pulita. Clicca la X di nuovo per mostrare tutto.", Toast.LENGTH_SHORT).show()
            }

            binding.mapView.invalidate()
        }
    }

    private fun checkLocationPermissions() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            setupMyLocation()
        } else {
            requestPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    private fun setupMap() {
        val mapController = binding.mapView.controller
        mapController.setZoom(15.0)
        mapController.setCenter(GeoPoint(44.4949, 11.3426))
        binding.mapView.minZoomLevel = 3.0
        binding.mapView.setMultiTouchControls(true)

        // Event listener per gestire la chiusura delle info window tramite touch sulla mappa
        val ricevitoreClickMappa = object : org.osmdroid.events.MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                org.osmdroid.views.overlay.infowindow.InfoWindow.closeAllInfoWindowsOn(binding.mapView)
                return false
            }

            override fun longPressHelper(p: GeoPoint?): Boolean {
                return false
            }
        }

        val mapEventsOverlay = org.osmdroid.views.overlay.MapEventsOverlay(ricevitoreClickMappa)
        binding.mapView.overlays.add(mapEventsOverlay)
    }

    private fun setupMyLocation() {
        myLocationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(requireContext()), binding.mapView)
        myLocationOverlay.enableMyLocation()
        myLocationOverlay.enableFollowLocation()
        binding.mapView.overlays.add(myLocationOverlay)
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
        if (::myLocationOverlay.isInitialized) myLocationOverlay.enableMyLocation()
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
        if (::myLocationOverlay.isInitialized) myLocationOverlay.disableMyLocation()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

// Controller personalizzato per il popup (InfoWindow) associato al marker del parcheggio
class ParkInfoWindow(mapView: org.osmdroid.views.MapView, private val onTerminaClick: (Long) -> Unit)
    : org.osmdroid.views.overlay.infowindow.MarkerInfoWindow(R.layout.marker_info_window, mapView) {

    override fun onOpen(item: Any?) {
        val marker = item as? org.osmdroid.views.overlay.Marker ?: return
        val txtTitle = mView.findViewById<android.widget.TextView>(R.id.txtCustomTitle)
        val txtDescription = mView.findViewById<android.widget.TextView>(R.id.txtCustomDescription)
        txtTitle?.text = marker.title
        txtDescription?.text = marker.snippet
        val sessionId = marker.relatedObject as? Long ?: return

        mView.findViewById<android.widget.Button>(R.id.btnInfoWindowTermina)?.setOnClickListener {
            onTerminaClick(sessionId)
            close()
        }
    }
}