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
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import it.unibo.lam2026.parkmate.viewmodel.ParcheggioViewModel

class MapFragment : Fragment() {

    // Setup del ViewBinding specifico per i Fragment (evita memory leaks)
    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!
    private lateinit var myLocationOverlay: MyLocationNewOverlay
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

        // 3. Ascoltiamo il click sul bottone (versione aggiornata con le coordinate REALI!)
        binding.btnParkHere.setOnClickListener {
            // Prendiamo la posizione esatta dal GPS o dal centro della mappa
            val currentGeoPoint = if (::myLocationOverlay.isInitialized && myLocationOverlay.myLocation != null) {
                myLocationOverlay.myLocation
            } else {
                binding.mapView.mapCenter as GeoPoint
            }

            // Creiamo un'istanza del BottomSheet e lo "zainetto" (Bundle) con le coordinate
            val bottomSheet = ParkBottomSheetFragment()
            val bundleDati = Bundle()
            bundleDati.putDouble("LATITUDINE", currentGeoPoint.latitude)
            bundleDati.putDouble("LONGITUDINE", currentGeoPoint.longitude)
            bottomSheet.arguments = bundleDati

            bottomSheet.show(parentFragmentManager, "ParkBottomSheet")
        }

        // ---------------------------------------------------------
        // 4. NUOVO CODICE: DISEGNAMO I MARKER DEI PARCHEGGI ATTIVI
        // ---------------------------------------------------------
        viewModel.parcheggiAttivi.observe(viewLifecycleOwner) { listaAttivi ->

            // Pulizia selettiva dei vecchi marker (non tocca il GPS!)
            binding.mapView.overlays.removeAll { it is org.osmdroid.views.overlay.Marker }

            // Creiamo un marker per ogni parcheggio attivo
            for (parcheggio in listaAttivi) {
                val segnaposto = org.osmdroid.views.overlay.Marker(binding.mapView)
                segnaposto.position = org.osmdroid.util.GeoPoint(parcheggio.latitudine, parcheggio.longitudine)

                segnaposto.title = "🚗 ${parcheggio.veicoloNome}"
                segnaposto.snippet = parcheggio.tipoParcheggio

                segnaposto.setAnchor(org.osmdroid.views.overlay.Marker.ANCHOR_CENTER, org.osmdroid.views.overlay.Marker.ANCHOR_BOTTOM)

                binding.mapView.overlays.add(segnaposto)
            }

            // Diciamo alla mappa di ridisegnarsi
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