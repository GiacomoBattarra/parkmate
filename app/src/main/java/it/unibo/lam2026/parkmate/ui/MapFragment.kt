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
import it.unibo.lam2026.parkmate.viewmodel.MainViewModel
import it.unibo.lam2026.parkmate.viewmodel.MainViewModelFactory
import it.unibo.lam2026.parkmate.viewmodel.PosizioniSalvateViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class MapFragment : Fragment() {

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!
    private lateinit var myLocationOverlay: MyLocationNewOverlay

    // Variabili per capire in che "modalità" ci troviamo
    private var isSelectingLocation = false
    private var isSelectingFavorite = false

    // NUOVE VARIABILI PER LA MODIFICA
    private var isEditingFavorite = false
    private var locationBeingEdited: PosizioneSalvata? = null

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

        // ---------------------------------------------------------
        // 1. DISEGNO DEI PARCHEGGI ATTIVI SULLA MAPPA
        // ---------------------------------------------------------
        viewModel.listaParcheggi.observe(viewLifecycleOwner) { lista ->
            binding.mapView.overlays.removeAll { it is org.osmdroid.views.overlay.Marker && it.id == "PARCHEGGIO" }

            if (!lista.isNullOrEmpty()) {
                val sostaAttiva = lista[0]
                binding.btnTerminaSosta.visibility = View.VISIBLE
                binding.btnTerminaSosta.setOnClickListener {
                    viewModel.terminaParcheggio(sostaAttiva.id)
                    Toast.makeText(requireContext(), "Parcheggio terminato!", Toast.LENGTH_SHORT).show()
                }

                for (parcheggio in lista) {
                    val segnaposto = org.osmdroid.views.overlay.Marker(binding.mapView)
                    segnaposto.id = "PARCHEGGIO"
                    segnaposto.position = org.osmdroid.util.GeoPoint(parcheggio.latitudine, parcheggio.longitudine)
                    segnaposto.title = "🚗 ${parcheggio.veicoloNome}"
                    java.lang.String.valueOf(parcheggio.tipoParcheggio).also { segnaposto.snippet = it }

                    segnaposto.relatedObject = parcheggio.id
                    segnaposto.infoWindow = ParkInfoWindow(binding.mapView) { idSessione ->
                        viewModel.terminaParcheggio(idSessione)
                        Toast.makeText(requireContext(), "Sosta terminata dalla mappa!", Toast.LENGTH_SHORT).show()
                    }

                    segnaposto.setOnMarkerClickListener { marker, mapView ->
                        if (marker.isInfoWindowOpen) marker.closeInfoWindow()
                        else {
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
                                } else addr.getAddressLine(0)

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
                binding.btnTerminaSosta.visibility = View.GONE
            }
            binding.mapView.invalidate()
        }

        // ---------------------------------------------------------
        // 2. DISEGNO DEI LUOGHI PREFERITI E GESTIONE BOTTONI MATITA/CESTINO
        // ---------------------------------------------------------
        val posizioniAdapter = PosizioniSalvateAdapter(
            posizioni = emptyList(),

            // AZIONE 1: Tocco il nome -> Sposto la mappa
            onPosizioneClick = { posizioneSalvata ->
                val mapController = binding.mapView.controller
                mapController.animateTo(org.osmdroid.util.GeoPoint(posizioneSalvata.latitudine, posizioneSalvata.longitudine))

                binding.recyclerPosizioniSalvate.visibility = View.GONE
                binding.headerLuoghiSalvati.text = "I tuoi luoghi salvati ▼"
            },

            // AZIONE 2: Tocco la MATITA -> Entro in Modalità Modifica Posizione
            onEditClick = { posizioneSalvata ->
                // Chiude la tendina
                binding.recyclerPosizioniSalvate.visibility = View.GONE
                binding.headerLuoghiSalvati.text = "I tuoi luoghi salvati ▼"

                // Salviamo quale luogo stiamo modificando e cambiamo modalità
                isEditingFavorite = true
                locationBeingEdited = posizioneSalvata

                // Facciamo comparire il bersaglio e cambiamo il bottone in basso a destra
                binding.imgCenterPin.visibility = View.VISIBLE
                binding.fabAggiungiPosizione.setImageResource(android.R.drawable.ic_menu_save)

                // Spostiamo la mappa sulle VECCHIE coordinate, per far ripartire l'utente da lì
                val mapController = binding.mapView.controller
                mapController.animateTo(org.osmdroid.util.GeoPoint(posizioneSalvata.latitudine, posizioneSalvata.longitudine))

                Toast.makeText(requireContext(), "Sposta il bersaglio sulla nuova posizione e premi il pulsante blu", Toast.LENGTH_LONG).show()

                if (::myLocationOverlay.isInitialized) myLocationOverlay.disableFollowLocation()
            },

            // AZIONE 3: Tocco il CESTINO -> Pop up di Conferma Eliminazione
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
                markerCuore.id = "PREFERITO"
                markerCuore.position = org.osmdroid.util.GeoPoint(posizione.latitudine, posizione.longitudine)
                markerCuore.title = posizione.nome

                val icona = ContextCompat.getDrawable(requireContext(), R.drawable.ic_heart)
                icona?.setTint(android.graphics.Color.RED)
                markerCuore.icon = icona

                markerCuore.setAnchor(org.osmdroid.views.overlay.Marker.ANCHOR_CENTER, org.osmdroid.views.overlay.Marker.ANCHOR_BOTTOM)

                markerCuore.setOnMarkerClickListener { marker, _ ->
                    if (marker.isInfoWindowOpen) marker.closeInfoWindow() else marker.showInfoWindow()
                    true
                }

                binding.mapView.overlays.add(markerCuore)
            }
            binding.mapView.invalidate()
        }

        // 3. BOTTONE "PARCHEGGIA QUI"
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
                binding.btnParkHere.setImageResource(android.R.drawable.ic_menu_mylocation)
            }
        }

        // 4. MENU A TENDINA
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

        // 5. BOTTONE "+" AGGIUNGI / CONFERMA MODIFICA PREFERITO
        binding.fabAggiungiPosizione.setOnClickListener {
            if (isSelectingLocation) return@setOnClickListener

            // --- FASE DI CONFERMA MODIFICA (Matita) ---
            if (isEditingFavorite) {
                val currentGeoPoint = binding.mapView.mapCenter as GeoPoint
                isEditingFavorite = false
                binding.imgCenterPin.visibility = View.GONE
                binding.fabAggiungiPosizione.setImageResource(android.R.drawable.ic_input_add)

                val campoTesto = android.widget.EditText(requireContext())
                campoTesto.setText(locationBeingEdited?.nome) // Precompila con il vecchio nome

                android.app.AlertDialog.Builder(requireContext())
                    .setTitle("Conferma Modifica")
                    .setMessage("Modifica il nome se lo desideri:")
                    .setView(campoTesto)
                    .setPositiveButton("Aggiorna") { dialog, _ ->
                        val nomeInserito = campoTesto.text.toString()
                        if (nomeInserito.isNotBlank() && locationBeingEdited != null) {
                            // Copiamo l'oggetto aggiornando NOME e nuove COORDINATE
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

                return@setOnClickListener // Esci per non far partire l'aggiunta di un nuovo luogo
            }

            // --- FASE DI AGGIUNTA NUOVO LUOGO (+) ---
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

        mView.findViewById<android.widget.Button>(R.id.btnInfoWindowTermina)?.setOnClickListener {
            onTerminaClick(sessionId)
            close()
        }
    }
}