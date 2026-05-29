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
import it.unibo.lam2026.parkmate.viewmodel.ParcheggioViewModel
import android.location.Geocoder
import androidx.lifecycle.lifecycleScope
import it.unibo.lam2026.parkmate.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import org.osmdroid.views.overlay.Polygon
class MapFragment : Fragment() {

    // Setup del ViewBinding specifico per i Fragment (evita memory leaks)
    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!
    private lateinit var myLocationOverlay: MyLocationNewOverlay
    private var isSelectingLocation = false
    private val viewModel: ParcheggioViewModel by viewModels()

    // Questo oggetto gestisce la richiesta del permesso e la risposta dell'utente
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        if (granted) {
            // Se l'utente accetta, attiviamo la localizzazione
            setupMyLocation()
        } else {
            // Se l'utente rifiuta, dovresti spiegare perché l'app ne ha bisogno
            // o disabilitare le funzioni legate al GPS
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Configurazione OsmDroid: va fatta PRIMA di creare l'interfaccia!
        // Nei Fragment, al posto di 'applicationContext' si usa 'requireContext()'
        Configuration.getInstance().load(
            requireContext(),
            PreferenceManager.getDefaultSharedPreferences(requireContext())
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Colleghiamo il layout fragment_map.xml
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. Ora che la view esiste, possiamo impostare la mappa
        setupMap()

        // 2. Controlla i permessi e accende il GPS in modo sicuro
        checkLocationPermissions()

        // 3. Ascoltiamo il click sul bottone (Modalità Dinamica)
        binding.btnParkHere.setOnClickListener {

            if (!isSelectingLocation) {
                // FASE A: Entriamo in modalità "Scegli Posizione"
                isSelectingLocation = true

                // Mostriamo il pin rosso al centro
                binding.imgCenterPin.visibility = View.VISIBLE

                // Cambiamo l'icona del bottone per far capire che ora serve a confermare (es. un floppy di salvataggio o check)
                binding.btnParkHere.setImageResource(android.R.drawable.ic_menu_save)

                // Piccolo avviso all'utente
                Toast.makeText(requireContext(), "Sposta la mappa e conferma la posizione", Toast.LENGTH_SHORT).show()

                // (Opzionale ma utile) Fermiamo l'inseguimento automatico del GPS per far scorrere la mappa liberamente
                if (::myLocationOverlay.isInitialized) {
                    myLocationOverlay.disableFollowLocation()
                }

            } else {
                // FASE B: L'utente ha spostato la mappa e preme per Confermare!

                // 1. Catturiamo il centro esatto del mirino
                val centerPoint = binding.mapView.mapCenter as GeoPoint

                // 2. Passiamo i dati al BottomSheet e lo apriamo
                val bottomSheet = ParkBottomSheetFragment()
                val bundleDati = Bundle()
                bundleDati.putDouble("LATITUDINE", centerPoint.latitude)
                bundleDati.putDouble("LONGITUDINE", centerPoint.longitude)
                bottomSheet.arguments = bundleDati
                bottomSheet.show(parentFragmentManager, "ParkBottomSheet")

                // 3. Riportiamo l'interfaccia allo "Stato Normale" per la prossima volta
                isSelectingLocation = false
                binding.imgCenterPin.visibility = View.GONE
                binding.btnParkHere.setImageResource(android.R.drawable.ic_menu_mylocation)
            }
        }

        // 4. DISEGNAMO I MARKER DEI PARCHEGGI ATTIVI (Versione Definitiva con Bottone!)
        viewModel.parcheggiAttivi.observe(viewLifecycleOwner) { listaAttivi ->

            binding.mapView.overlays.removeAll { it is org.osmdroid.views.overlay.Marker }

            for (parcheggio in listaAttivi) {
                val segnaposto = org.osmdroid.views.overlay.Marker(binding.mapView)
                segnaposto.position = org.osmdroid.util.GeoPoint(parcheggio.latitudine, parcheggio.longitudine)

                segnaposto.title = "🚗 ${parcheggio.veicoloNome}"
                java.lang.String.valueOf(parcheggio.tipoParcheggio).also { segnaposto.snippet = it }

                // -----------------------------------------------------------------
                // [NOVITÀ 1]: "Nascondiamo" l'ID del parcheggio dentro al marker!
                // Ci servirà per sapere quale sessione chiudere al click del bottone
                segnaposto.relatedObject = parcheggio.id

                // [NOVITÀ 2]: Gli assegniamo il nostro fumetto personalizzato col bottone!
                segnaposto.infoWindow = ParkInfoWindow(binding.mapView) { idSessione ->
                    // Chiamiamo il viewModel per terminare la sosta!
                    viewModel.terminaParcheggio(idSessione)
                    android.widget.Toast.makeText(requireContext(), "Sosta terminata direttamente dalla mappa!", android.widget.Toast.LENGTH_SHORT).show()
                }

                segnaposto.setOnMarkerClickListener { marker, mapView ->
                    if (marker.isInfoWindowOpen) {
                        // Se è già aperto, il secondo click lo CHIUDE!
                        marker.closeInfoWindow()
                    } else {
                        // Se è chiuso, prima chiudiamo eventuali altri fumetti aperti sulla mappa (per pulizia)...
                        org.osmdroid.views.overlay.infowindow.InfoWindow.closeAllInfoWindowsOn(mapView)
                        // ...e poi APRIAMO questo!
                        marker.showInfoWindow()
                    }
                    true // Comunichiamo ad OSMDroid che abbiamo gestito il click noi manualmente
                }

                // Il Geocoder in background per la via (Versione Pulita: solo Via, Civico e Città)
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val geocoder = Geocoder(requireContext(), Locale.getDefault())
                        val indirizzi = geocoder.getFromLocation(parcheggio.latitudine, parcheggio.longitudine, 1)

                        if (!indirizzi.isNullOrEmpty()) {
                            val addr = indirizzi[0]

                            // 1. Estraiamo singolarmente solo i pezzi che ci interessano!
                            val via = addr.thoroughfare ?: ""      // Es. "Via Castiglione"
                            val civico = addr.subThoroughfare ?: "" // Es. "89"
                            val citta = addr.locality ?: ""        // Es. "Bologna"

                            // 2. Assembliamo l'indirizzo in modo elegante ed escludiamo CAP e Nazione
                            val indirizzoPulito = if (via.isNotEmpty() && citta.isNotEmpty()) {
                                if (civico.isNotEmpty()) "$via $civico, $citta" else "$via, $citta"
                            } else {
                                // Fallback di sicurezza: se per qualche motivo i campi sopra sono vuoti, usa la riga intera
                                addr.getAddressLine(0)
                            }

                            withContext(Dispatchers.Main) {
                                segnaposto.snippet = "${parcheggio.tipoParcheggio}\n📍 $indirizzoPulito"
                                if (segnaposto.isInfoWindowOpen) {
                                    segnaposto.closeInfoWindow()
                                    segnaposto.showInfoWindow()
                                }
                            }
                        }
                    } catch (e: Exception) { }
                }

                segnaposto.setAnchor(org.osmdroid.views.overlay.Marker.ANCHOR_CENTER, org.osmdroid.views.overlay.Marker.ANCHOR_BOTTOM)
                binding.mapView.overlays.add(segnaposto)
            }

            binding.mapView.invalidate()
        }
    }

    private fun checkLocationPermissions() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                // Il permesso è già stato concesso in precedenza
                setupMyLocation()
            }
            else -> {
                // Chiediamo i permessi all'utente
                requestPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        }
    }

    private fun setupMap() {
        val mapController = binding.mapView.controller
        mapController.setZoom(15.0)
        val startPoint = GeoPoint(44.4949, 11.3426) // Coordinate di Bologna
        mapController.setCenter(startPoint)

        binding.mapView.minZoomLevel = 3.0
        binding.mapView.setMultiTouchControls(true)
    }

    // Configura il livello della posizione sulla mappa
    private fun setupMyLocation() {
        val locationProvider = GpsMyLocationProvider(requireContext())
        myLocationOverlay = MyLocationNewOverlay(locationProvider, binding.mapView)

        myLocationOverlay.enableMyLocation() // Mostra la posizione attuale
        myLocationOverlay.enableFollowLocation() // Centra la mappa mentre ti muovi

        binding.mapView.overlays.add(myLocationOverlay)
    }
    // Gestione OBBLIGATORIA del ciclo di vita della mappa di OsmDroid
    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()

        // Riaccendiamo il GPS quando l'utente torna sull'app
        if (::myLocationOverlay.isInitialized) {
            myLocationOverlay.enableMyLocation()
        }
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()

        // Spegniamo il GPS se l'app va in background per salvare batteria
        if (::myLocationOverlay.isInitialized) {
            myLocationOverlay.disableMyLocation()
        }
    }

    // Pulizia della memoria quando il Fragment viene distrutto
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

// Questa classe personalizza il comportamento della finestrella del Marker
class ParkInfoWindow(mapView: org.osmdroid.views.MapView, private val onTerminaClick: (Long) -> Unit)
    : org.osmdroid.views.overlay.infowindow.MarkerInfoWindow(R.layout.marker_info_window, mapView) {

    override fun onOpen(item: Any?) {
        val marker = item as? org.osmdroid.views.overlay.Marker ?: return

        // Colleghiamo i testi usando i nuovi ID unici che OSMDroid non può intercettare!
        val txtTitle = mView.findViewById<android.widget.TextView>(R.id.txtCustomTitle)
        val txtDescription = mView.findViewById<android.widget.TextView>(R.id.txtCustomDescription)

        // Scriviamo i dati reali nel fumetto
        txtTitle?.text = marker.title
        txtDescription?.text = marker.snippet

        // Recuperiamo l'ID della sosta per il tasto Termina
        val sessionId = marker.relatedObject as? Long ?: return

        val btnTermina = mView.findViewById<android.widget.Button>(R.id.btnInfoWindowTermina)
        btnTermina?.setOnClickListener {
            onTerminaClick(sessionId)
            close()
        }
    }
}