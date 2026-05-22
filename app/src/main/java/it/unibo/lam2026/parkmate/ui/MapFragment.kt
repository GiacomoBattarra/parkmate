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

class MapFragment : Fragment() {

    // Setup del ViewBinding specifico per i Fragment (evita memory leaks)
    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!
    private lateinit var myLocationOverlay: MyLocationNewOverlay

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
        // Ora che la view esiste, possiamo impostare la mappa
        setupMap()
        // Invece di chiamare direttamente setupMyLocation(), controlliamo i permessi
        checkLocationPermissions()

        // Ascoltiamo il click sul nuovo bottone
        binding.btnParkHere.setOnClickListener {

            // Creiamo un'istanza del BottomSheet che hai appena aggiunto al progetto
            val bottomSheet = ParkBottomSheetFragment()

            // Lo mostriamo usando il FragmentManager del Fragment "padre" (MapFragment)
            // "ParkBottomSheet" è solo un'etichetta (tag) interna per il sistema
            bottomSheet.show(parentFragmentManager, "ParkBottomSheet")

//            // Proviamo a prendere la posizione esatta dal pallino blu del GPS
//            val currentGeoPoint = if (::myLocationOverlay.isInitialized && myLocationOverlay.myLocation != null) {
//                myLocationOverlay.myLocation
//            } else {
//                // Se il GPS non ha ancora agganciato il segnale o l'utente ha negato i permessi,
//                // prendiamo il centro esatto visualizzato sulla mappa in quel momento.
//                // Questo soddisfa anche il requisito di poter "aggiustare manualmente" la posizione!
//                binding.mapView.mapCenter as GeoPoint
//            }
//
//            val lat = currentGeoPoint.latitude
//            val lon = currentGeoPoint.longitude
//
//            // Per ora mostriamo un semplice messaggio a schermo (Toast) per verificare che funzioni.
//            // Nello step successivo, qui apriremo un Dialog o un nuovo Fragment per far
//            // scegliere all'utente quale veicolo parcheggiare.
//            android.widget.Toast.makeText(
//                requireContext(),
//                "Inizio parcheggio a: Lat $lat, Lon $lon",
//                android.widget.Toast.LENGTH_LONG
//            ).show()
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