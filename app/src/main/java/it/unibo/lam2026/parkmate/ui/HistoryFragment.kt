package it.unibo.lam2026.parkmate.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import it.unibo.lam2026.parkmate.databinding.FragmentHistoryBinding
import it.unibo.lam2026.parkmate.model.SessioneParcheggio
import it.unibo.lam2026.parkmate.viewmodel.ParcheggioViewModel
import kotlinx.coroutines.launch

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ParcheggioViewModel by viewModels()

    private lateinit var adapter: HistoryAdapter
    private var listaCompletaStorico: List<SessioneParcheggio> = emptyList()
    private var isSpinnerVeicoliPronto = false

    // Memoria per sapere se stiamo guardando la mappa o la lista
    private var isMapVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Inizializziamo OSMDroid (obbligatorio per usare la mappa)
        Configuration.getInstance().load(
            requireContext(),
            PreferenceManager.getDefaultSharedPreferences(requireContext())
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. Configuriamo la Mappa di base
        binding.mapViewHistory.setMultiTouchControls(true)
        binding.mapViewHistory.controller.setZoom(13.0)
        binding.mapViewHistory.controller.setCenter(GeoPoint(44.4949, 11.3426)) // Centro generico

        // 2. Bottone Toggle: Scambia tra Mappa e Lista
        binding.btnToggleView.setOnClickListener {
            isMapVisible = !isMapVisible
            if (isMapVisible) {
                binding.recyclerViewHistory.visibility = View.GONE
                binding.mapViewHistory.visibility = View.VISIBLE
                binding.btnToggleView.setImageResource(android.R.drawable.ic_menu_sort_by_size) // Icona lista
            } else {
                binding.mapViewHistory.visibility = View.GONE
                binding.recyclerViewHistory.visibility = View.VISIBLE
                binding.btnToggleView.setImageResource(android.R.drawable.ic_dialog_map) // Icona mappa
            }
        }

        // 3. Configuriamo la lista
        binding.recyclerViewHistory.layoutManager = LinearLayoutManager(requireContext())
        adapter = HistoryAdapter(
            storicoList = emptyList(),
            onTerminaClick = { sessioneDaChiudere -> viewModel.terminaParcheggio(sessioneDaChiudere.id) },
            onEliminaClick = { sessioneDaEliminare -> viewModel.cancellaParcheggio(sessioneDaEliminare.id) }
        )
        binding.recyclerViewHistory.adapter = adapter

        // 4. Configuriamo la tendina del Tipo
        val opzioniFiltroTipo = arrayOf("Tutti i Tipi", "Solo Liberi", "Solo Orario", "Solo Fissi")
        val spinnerTipoAdapter = android.widget.ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, opzioniFiltroTipo)
        spinnerTipoAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerFiltroTipo.adapter = spinnerTipoAdapter

        // 5. Ascoltiamo i click sulle tendine
        val listenerFiltri = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                applicaFiltro()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
        binding.spinnerFiltroTipo.onItemSelectedListener = listenerFiltri
        binding.spinnerFiltroVeicolo.onItemSelectedListener = listenerFiltri

        // 6. Osserviamo il Database
        viewModel.storicoParcheggi.observe(viewLifecycleOwner) { resParcheggi ->
            listaCompletaStorico = resParcheggi

            val veicoliUnici = resParcheggi.map { it.veicoloNome }.distinct()
            val opzioniVeicolo = mutableListOf("Tutti i Veicoli")
            opzioniVeicolo.addAll(veicoliUnici)

            val currentAdapter = binding.spinnerFiltroVeicolo.adapter
            if (currentAdapter == null || currentAdapter.count != opzioniVeicolo.size) {
                val spinnerVeicoloAdapter = android.widget.ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, opzioniVeicolo)
                spinnerVeicoloAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                binding.spinnerFiltroVeicolo.adapter = spinnerVeicoloAdapter
            }

            isSpinnerVeicoliPronto = true
            applicaFiltro()
        }
    }

    private fun applicaFiltro() {
        if (_binding == null || !isSpinnerVeicoliPronto) return

        val selezioneTipo = binding.spinnerFiltroTipo.selectedItemPosition
        val selezioneVeicolo = binding.spinnerFiltroVeicolo.selectedItem as? String ?: "Tutti i Veicoli"

        var listaFiltrata = listaCompletaStorico

        // Filtro 1: Tipo
        listaFiltrata = when (selezioneTipo) {
            1 -> listaFiltrata.filter { it.tipoParcheggio.contains("Libero", ignoreCase = true) }
            2 -> listaFiltrata.filter { it.tipoParcheggio.contains("Orario", ignoreCase = true) }
            3 -> listaFiltrata.filter { it.tipoParcheggio.contains("Fiss", ignoreCase = true) }
            else -> listaFiltrata
        }

        // Filtro 2: Veicolo
        if (selezioneVeicolo != "Tutti i Veicoli") {
            listaFiltrata = listaFiltrata.filter { it.veicoloNome == selezioneVeicolo }
        }

        // Aggiorniamo la LISTA...
        adapter.aggiornaDati(listaFiltrata)

        // ...E AGGIORNIAMO LA MAPPA!
        aggiornaMappaStorico(listaFiltrata)
    }

    private fun aggiornaMappaStorico(lista: List<SessioneParcheggio>) {
        binding.mapViewHistory.overlays.clear()

        for (parcheggio in lista) {
            val marker = org.osmdroid.views.overlay.Marker(binding.mapViewHistory)
            marker.position = GeoPoint(parcheggio.latitudine, parcheggio.longitudine)

            // 1. Prima riga in alto (Titolo del fumetto) -> Il Veicolo
            marker.title = "🚗 ${parcheggio.veicoloNome}"

            // Prepariamo la Data
            val formattaData = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
            val data = formattaData.format(java.util.Date(parcheggio.startTimeStamp))

            // Prepariamo il Costo
            var costoTesto = "N/A"
            if (parcheggio.isAttivo) {
                costoTesto = "In corso..."
            } else if (parcheggio.costoTotale != null) {
                costoTesto = if (parcheggio.costoTotale == 0.0) "Gratis"
                else String.format(java.util.Locale.getDefault(), "€%.2f", parcheggio.costoTotale)
            }

            // Impostiamo un testo temporaneo mentre cerca la via su internet
            marker.snippet = "📅 Data: $data\n" + "📍 Ricerca indirizzo...\n" + "🅿️ Tipo: ${parcheggio.tipoParcheggio}\n" + "💰 Costo: $costoTesto"
            marker.setAnchor(org.osmdroid.views.overlay.Marker.ANCHOR_CENTER, org.osmdroid.views.overlay.Marker.ANCHOR_BOTTOM)

            // Diciamo al marker di usare il nostro nuovo fumetto che supporta le righe multiple!
            marker.infoWindow = HistoryInfoWindow(binding.mapViewHistory)

            binding.mapViewHistory.overlays.add(marker)

            // --- MAGIA: CHIAMIAMO IL GEOCODER IN BACKGROUND ---
            // Usa viewLifecycleOwner.lifecycleScope per non bloccare lo schermo
            viewLifecycleOwner.lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val geocoder = android.location.Geocoder(requireContext(), java.util.Locale.getDefault())
                    val indirizzi = geocoder.getFromLocation(parcheggio.latitudine, parcheggio.longitudine, 1)

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
                        "Coordinate sconosciute"
                    }

                    // Torniamo sul Thread principale per aggiornare la grafica
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        // Inseriamo l'ordine esatto che hai chiesto: Data, Via, Tipo e Costo!
                        marker.snippet = "📅 Data: $data\n" + "📍 $indirizzoPulito\n" + "🅿️ Tipo: ${parcheggio.tipoParcheggio}\n" + "💰 Costo: $costoTesto"

                        // Se l'utente ha il fumetto aperto proprio ora, lo "riavviamo" per mostrare la via
                        if (marker.isInfoWindowOpen) {
                            marker.closeInfoWindow()
                            marker.showInfoWindow()
                        }
                    }
                } catch (e: Exception) {
                    // Fallback se l'emulatore è offline
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        val latCorta = String.format(java.util.Locale.getDefault(), "%.4f", parcheggio.latitudine)
                        val lonCorta = String.format(java.util.Locale.getDefault(), "%.4f", parcheggio.longitudine)
                        marker.snippet = "📅 Data: $data\n" + "📍 Coord: $latCorta, $lonCorta\n" + "🅿️ Tipo: ${parcheggio.tipoParcheggio}\n" + "💰 Costo: $costoTesto"
                        if (marker.isInfoWindowOpen) {
                            marker.closeInfoWindow()
                            marker.showInfoWindow()
                        }
                    }
                }
            }
        }

        binding.mapViewHistory.invalidate()

        // Se c'è almeno un risultato, spostiamo l'inquadratura sul più recente
        if (lista.isNotEmpty()) {
            binding.mapViewHistory.controller.animateTo(GeoPoint(lista[0].latitudine, lista[0].longitudine))
        }
    }

    // Gestione del ciclo di vita della mappa
    override fun onResume() {
        super.onResume()
        binding.mapViewHistory.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.mapViewHistory.onPause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ---------------------------------------------------------
// Classe HistoryInfoWindow (Fumetto personalizzato a più righe)
// ---------------------------------------------------------
    class HistoryInfoWindow(mapView: org.osmdroid.views.MapView)
        : org.osmdroid.views.overlay.infowindow.MarkerInfoWindow(it.unibo.lam2026.parkmate.R.layout.marker_info_window, mapView) {

        override fun onOpen(item: Any?) {
            val marker = item as? org.osmdroid.views.overlay.Marker ?: return

            // Colleghiamo i testi del tuo layout personalizzato
            val txtTitle = mView.findViewById<android.widget.TextView>(it.unibo.lam2026.parkmate.R.id.txtCustomTitle)
            val txtDescription = mView.findViewById<android.widget.TextView>(it.unibo.lam2026.parkmate.R.id.txtCustomDescription)

            txtTitle?.text = marker.title
            txtDescription?.text = marker.snippet

            // Visto che siamo nello storico, nascondiamo il bottone "Termina Sosta" per non confondere l'utente!
            val btnTermina = mView.findViewById<android.widget.Button>(it.unibo.lam2026.parkmate.R.id.btnInfoWindowTermina)
            btnTermina?.visibility = android.view.View.GONE
        }
    }
}