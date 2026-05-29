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
import it.unibo.lam2026.parkmate.viewmodel.MainViewModel
import it.unibo.lam2026.parkmate.viewmodel.MainViewModelFactory
import it.unibo.lam2026.parkmate.viewmodel.PosizioniSalvateViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class MapFragment : Fragment() {

    // Setup del ViewBinding specifico per i Fragment
    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!
    private lateinit var myLocationOverlay: MyLocationNewOverlay

    // Variabili per la modalità dinamica
    private var isSelectingLocation = false
    private var isSelectingFavorite = false

    // ViewModel del tuo amico (Architettura aggiornata)
    private lateinit var viewModel: MainViewModel

    // Il tuo ViewModel per i preferiti
    private val posizioniViewModel: PosizioniSalvateViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        if (granted) {
            setupMyLocation()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().load(
            requireContext(),
            PreferenceManager.getDefaultSharedPreferences(requireContext())
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. Inizializzazione ViewModel e Repository (Codice nuovo del tuo amico!)
        val dao = AppDatabase.getDatabase(requireContext()).parcheggioDao()
        val repository = ParcheggioRepository(dao)
        val factory = MainViewModelFactory(repository)
        viewModel = ViewModelProvider(this, factory)[MainViewModel::class.java]

        setupMap()
        checkLocationPermissions()

        // 2. Osserviamo la lista dei parcheggi attivi per gestire il bottone "Termina Sosta"
        // E per disegnare i TUOI marker sulla mappa!
        viewModel.listaParcheggi.observe(viewLifecycleOwner) { lista ->

            // Pulizia vecchi marker
            binding.mapView.overlays.removeAll { it is org.osmdroid.views.overlay.Marker }

            if (!lista.isNullOrEmpty()) {
                // Logica del tuo amico per il bottone in basso a sinistra
                val sostaAttiva = lista[0] // Prende la prima sosta attiva trovata
                binding.btnTerminaSosta.visibility = View.VISIBLE
                binding.btnTerminaSosta.setOnClickListener {
                    viewModel.terminaParcheggio(sostaAttiva.id)
                    Toast.makeText(requireContext(), "Parcheggio terminato!", Toast.LENGTH_SHORT).show()
                }

                // La TUA logica per disegnare i parcheggi sulla mappa
                for (parcheggio in lista) {
                    val segnaposto = org.osmdroid.views.overlay.Marker(binding.mapView)
                    segnaposto.position = org.osmdroid.util.GeoPoint(parcheggio.latitudine, parcheggio.longitudine)
                    segnaposto.title = "🚗 ${parcheggio.veicoloNome}"
                    java.lang.String.valueOf(parcheggio.tipoParcheggio).also { segnaposto.snippet = it }

                    segnaposto.relatedObject = parcheggio.id
                    segnaposto.infoWindow = ParkInfoWindow(binding.mapView) { idSessione ->
                        viewModel.terminaParcheggio(idSessione)
                        Toast.makeText(requireContext(), "Sosta terminata direttamente dalla mappa!", Toast.LENGTH_SHORT).show()
                    }

                    segnaposto.setOnMarkerClickListener { marker, mapView ->
                        if (marker.isInfoWindowOpen) {
                            marker.closeInfoWindow()
                        } else {
                            org.osmdroid.views.overlay.infowindow.InfoWindow.closeAllInfoWindowsOn(mapView)
                            marker.showInfoWindow()
                        }
                        true
                    }

                    lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            val geocoder = Geocoder(requireContext(), Locale.getDefault())
                            val indirizzi = geocoder.getFromLocation(parcheggio.latitudine, parcheggio.longitudine, 1)

                            if (!indirizzi.isNullOrEmpty()) {
                                val addr = indirizzi[0]
                                val via = addr.thoroughfare ?: ""
                                val civico = addr.subThoroughfare ?: ""
                                val citta = addr.locality ?: ""

                                val indirizzoPulito = if (via.isNotEmpty() && citta.isNotEmpty()) {
                                    if (civico.isNotEmpty()) "$via $civico, $citta" else "$via, $citta"
                                } else {
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
            } else {
                // Se non ci sono parcheggi attivi, nascondiamo il bottone
                binding.btnTerminaSosta.visibility = View.GONE
            }

            // Diciamo alla mappa di ridisegnarsi
            binding.mapView.invalidate()
        }

        // 3. BOTTONE "PARCHEGGIA QUI"
        binding.btnParkHere.setOnClickListener {
            if (isSelectingFavorite) return@setOnClickListener

            if (!isSelectingLocation) {
                isSelectingLocation = true
                binding.imgCenterPin.visibility = View.VISIBLE
                binding.btnParkHere.setImageResource(android.R.drawable.ic_menu_save)
                Toast.makeText(requireContext(), "Sposta la mappa e conferma la posizione", Toast.LENGTH_SHORT).show()

                if (::myLocationOverlay.isInitialized) {
                    myLocationOverlay.disableFollowLocation()
                }
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
                binding.btnParkHere.setImageResource(android.R.drawable.ic_menu_mylocation)
            }
        }

        // 5. GESTIONE LUOGHI SALVATI (Il TUO codice!)
        val posizioniAdapter = PosizioniSalvateAdapter(
            posizioni = emptyList(),
            onPosizioneClick = { posizioneSalvata ->
                Toast.makeText(requireContext(), "Andiamo a: ${posizioneSalvata.nome}", Toast.LENGTH_SHORT).show()
                val mapController = binding.mapView.controller
                mapController.animateTo(
                    org.osmdroid.util.GeoPoint(posizioneSalvata.latitudine, posizioneSalvata.longitudine)
                )
            },
            onDeleteClick = { posizioneSalvata ->
                posizioniViewModel.eliminaPosizione(posizioneSalvata)
                Toast.makeText(requireContext(), "Eliminato: ${posizioneSalvata.nome}", Toast.LENGTH_SHORT).show()
            }
        )

        binding.recyclerPosizioniSalvate.adapter = posizioniAdapter

        posizioniViewModel.posizioniSalvate.observe(viewLifecycleOwner) { listaAggiornata ->
            posizioniAdapter.updateData(listaAggiornata)
        }

        // Bottone "+" per i Preferiti
        binding.fabAggiungiPosizione.setOnClickListener {
            if (isSelectingLocation) return@setOnClickListener

            if (!isSelectingFavorite) {
                isSelectingFavorite = true
                binding.imgCenterPin.visibility = View.VISIBLE
                binding.fabAggiungiPosizione.setImageResource(android.R.drawable.ic_menu_save)
                Toast.makeText(requireContext(), "Sposta la mappa sul luogo da salvare e premi di nuovo", Toast.LENGTH_LONG).show()

                if (::myLocationOverlay.isInitialized) {
                    myLocationOverlay.disableFollowLocation()
                }
            } else {
                val currentGeoPoint = binding.mapView.mapCenter as GeoPoint

                isSelectingFavorite = false
                binding.imgCenterPin.visibility = View.GONE
                binding.fabAggiungiPosizione.setImageResource(android.R.drawable.ic_input_add)

                val campoTesto = android.widget.EditText(requireContext())
                campoTesto.hint = "Es. Casa, Palestra, Lavoro..."

                android.app.AlertDialog.Builder(requireContext())
                    .setTitle("Salva questa posizione")
                    .setMessage("Inserisci un nome per ricordare questo luogo:")
                    .setView(campoTesto)
                    .setPositiveButton("Salva") { dialog, _ ->
                        val nomeInserito = campoTesto.text.toString()

                        if (nomeInserito.isNotBlank()) {
                            posizioniViewModel.salvaNuovaPosizione(
                                nomeLuogo = nomeInserito,
                                lat = currentGeoPoint.latitude,
                                lng = currentGeoPoint.longitude
                            )
                            Toast.makeText(requireContext(), "Salvato: $nomeInserito", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(requireContext(), "Il nome non può essere vuoto!", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .setNegativeButton("Annulla") { dialog, _ ->
                        dialog.dismiss()
                    }
                    .show()
            }
        }
    }

    private fun checkLocationPermissions() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                setupMyLocation()
            }
            else -> {
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
        val startPoint = GeoPoint(44.4949, 11.3426)
        mapController.setCenter(startPoint)
        binding.mapView.minZoomLevel = 3.0
        binding.mapView.setMultiTouchControls(true)
    }

    private fun setupMyLocation() {
        val locationProvider = GpsMyLocationProvider(requireContext())
        myLocationOverlay = MyLocationNewOverlay(locationProvider, binding.mapView)
        myLocationOverlay.enableMyLocation()
        myLocationOverlay.enableFollowLocation()
        binding.mapView.overlays.add(myLocationOverlay)
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
        if (::myLocationOverlay.isInitialized) {
            myLocationOverlay.enableMyLocation()
        }
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
        if (::myLocationOverlay.isInitialized) {
            myLocationOverlay.disableMyLocation()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

// ---------------------------------------------------------
// Classe ParkInfoWindow
// ---------------------------------------------------------
class ParkInfoWindow(mapView: org.osmdroid.views.MapView, private val onTerminaClick: (Long) -> Unit)
    : org.osmdroid.views.overlay.infowindow.MarkerInfoWindow(R.layout.marker_info_window, mapView) {

    override fun onOpen(item: Any?) {
        val marker = item as? org.osmdroid.views.overlay.Marker ?: return

        val txtTitle = mView.findViewById<android.widget.TextView>(R.id.txtCustomTitle)
        val txtDescription = mView.findViewById<android.widget.TextView>(R.id.txtCustomDescription)

        txtTitle?.text = marker.title
        txtDescription?.text = marker.snippet

        val sessionId = marker.relatedObject as? Long ?: return

        val btnTermina = mView.findViewById<android.widget.Button>(R.id.btnInfoWindowTermina)
        btnTermina?.setOnClickListener {
            onTerminaClick(sessionId)
            close()
        }
    }
}