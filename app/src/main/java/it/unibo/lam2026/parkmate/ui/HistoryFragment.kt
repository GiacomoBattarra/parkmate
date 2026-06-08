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
    private var isMapVisible = false

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
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.mapViewHistory.setMultiTouchControls(true)
        binding.mapViewHistory.controller.setZoom(13.0)
        binding.mapViewHistory.controller.setCenter(GeoPoint(44.4949, 11.3426))

        val ricevitoreClickMappa = object : org.osmdroid.events.MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                org.osmdroid.views.overlay.infowindow.InfoWindow.closeAllInfoWindowsOn(binding.mapViewHistory)
                return false
            }
            override fun longPressHelper(p: GeoPoint?): Boolean = false
        }
        binding.mapViewHistory.overlays.add(org.osmdroid.views.overlay.MapEventsOverlay(ricevitoreClickMappa))

        binding.btnToggleView.setOnClickListener {
            isMapVisible = !isMapVisible
            if (isMapVisible) {
                binding.recyclerViewHistory.visibility = View.GONE
                binding.mapViewHistory.visibility = View.VISIBLE
                binding.btnToggleView.setImageResource(android.R.drawable.ic_menu_sort_by_size)
            } else {
                binding.mapViewHistory.visibility = View.GONE
                binding.recyclerViewHistory.visibility = View.VISIBLE
                binding.btnToggleView.setImageResource(android.R.drawable.ic_dialog_map)
            }
        }

        binding.recyclerViewHistory.layoutManager = LinearLayoutManager(requireContext())
        adapter = HistoryAdapter(
            storicoList = emptyList(),
            onTerminaClick = { s -> viewModel.terminaParcheggio(s.id) },
            onEliminaClick = { s -> viewModel.cancellaParcheggio(s.id) }
        )
        binding.recyclerViewHistory.adapter = adapter

        val opzioniFiltroTipo = arrayOf("Tutti i Tipi", "Solo Gratis", "Solo Orario", "Solo Fissi")
        val spinnerTipoAdapter = android.widget.ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, opzioniFiltroTipo)
        spinnerTipoAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerFiltroTipo.adapter = spinnerTipoAdapter

        val listenerFiltri = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, pos: Int, id: Long) {
                applicaFiltro()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
        binding.spinnerFiltroTipo.onItemSelectedListener = listenerFiltri
        binding.spinnerFiltroVeicolo.onItemSelectedListener = listenerFiltri

        viewModel.storicoParcheggi.observe(viewLifecycleOwner) { resParcheggi ->
            listaCompletaStorico = resParcheggi

            // Normalizza i nomi dei veicoli rimuovendo il tag di eliminazione per evitare duplicati nel menu a tendina
            val veicoliUnici = resParcheggi.map { it.veicoloNome.replace(" [ELIMINATO]", "") }.distinct()
            val opzioniVeicolo = mutableListOf("Tutti i Veicoli")
            opzioniVeicolo.addAll(veicoliUnici)

            val currentAdapter = binding.spinnerFiltroVeicolo.adapter
            if (currentAdapter == null || currentAdapter.count != opzioniVeicolo.size) {
                val spinnerVeicoloAdapter = android.widget.ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, opzioniVeicolo)
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

        listaFiltrata = when (selezioneTipo) {
            1 -> listaFiltrata.filter { it.tipoParcheggio.contains("Gratis", ignoreCase = true) || it.tipoParcheggio.contains("Gratis", ignoreCase = true) }
            2 -> listaFiltrata.filter { it.tipoParcheggio.contains("Orario", ignoreCase = true) }
            3 -> listaFiltrata.filter { it.tipoParcheggio.contains("Fiss", ignoreCase = true) }
            else -> listaFiltrata
        }

        // Applica il filtro per nome veicolo, garantendo la corrispondenza anche per i veicoli contrassegnati come eliminati
        if (selezioneVeicolo != "Tutti i Veicoli") {
            listaFiltrata = listaFiltrata.filter { it.veicoloNome.replace(" [ELIMINATO]", "") == selezioneVeicolo }
        }

        adapter.aggiornaDati(listaFiltrata)
        aggiornaMappaStorico(listaFiltrata)
    }

    private fun aggiornaMappaStorico(lista: List<SessioneParcheggio>) {
        binding.mapViewHistory.overlays.removeAll { it is org.osmdroid.views.overlay.Marker }

        for (parcheggio in lista) {
            val marker = org.osmdroid.views.overlay.Marker(binding.mapViewHistory)
            marker.position = GeoPoint(parcheggio.latitudine, parcheggio.longitudine)

            val nomeReale = parcheggio.veicoloNome.replace(" [ELIMINATO]", "")
            val iconaMezzo = when {
                nomeReale.contains("Moto", ignoreCase = true) -> "🏍️"
                nomeReale.contains("Bici", ignoreCase = true) -> "🚲"
                else -> "🚗"
            }

            marker.title = if (parcheggio.veicoloNome.contains("[ELIMINATO]")) {
                "$iconaMezzo $nomeReale [ELIMINATO]"
            } else {
                "$iconaMezzo $nomeReale"
            }

            val iconaMarker = marker.icon?.constantState?.newDrawable()?.mutate()
            if (parcheggio.isAttivo) {
                iconaMarker?.clearColorFilter()
                iconaMarker?.alpha = 255
            } else {
                val matrix = android.graphics.ColorMatrix()
                matrix.setSaturation(0f)
                iconaMarker?.colorFilter = android.graphics.ColorMatrixColorFilter(matrix)
                iconaMarker?.alpha = 150
            }
            marker.icon = iconaMarker

            val formattaData = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
            val data = formattaData.format(java.util.Date(parcheggio.startTimeStamp))

            var costoTesto = "N/A"
            if (parcheggio.isAttivo) {
                costoTesto = "In corso..."
            } else if (parcheggio.costoTotale != null) {
                costoTesto = if (parcheggio.costoTotale == 0.0) "Gratis"
                else String.format(java.util.Locale.getDefault(), "€%.2f", parcheggio.costoTotale)
            }

            marker.snippet = "📅 Data: $data\n📍 Ricerca indirizzo in corso...\n🅿️ Tipo: ${parcheggio.tipoParcheggio}\n💰 Costo: $costoTesto"
            marker.setAnchor(org.osmdroid.views.overlay.Marker.ANCHOR_CENTER, org.osmdroid.views.overlay.Marker.ANCHOR_BOTTOM)

            marker.infoWindow = HistoryInfoWindow(binding.mapViewHistory)

            marker.setOnMarkerClickListener { m, mapView ->
                if (m.isInfoWindowOpen) m.closeInfoWindow()
                else {
                    org.osmdroid.views.overlay.infowindow.InfoWindow.closeAllInfoWindowsOn(mapView)
                    m.showInfoWindow()
                }
                true
            }

            binding.mapViewHistory.overlays.add(marker)

            // Risoluzione asincrona delle coordinate in indirizzo stradale (Geocoding inverso)
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
                        } else addr.getAddressLine(0) ?: "Indirizzo sconosciuto"
                    } else "Coordinate sconosciute"

                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        marker.snippet = "📅 Data: $data\n📍 $indirizzoPulito\n🅿️ Tipo: ${parcheggio.tipoParcheggio}\n💰 Costo: $costoTesto"
                        if (marker.isInfoWindowOpen) {
                            marker.closeInfoWindow()
                            marker.showInfoWindow()
                        }
                    }
                } catch (e: Exception) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        val latCorta = String.format(java.util.Locale.getDefault(), "%.4f", parcheggio.latitudine)
                        val lonCorta = String.format(java.util.Locale.getDefault(), "%.4f", parcheggio.longitudine)
                        marker.snippet = "📅 Data: $data\n📍 Non riesco a caricare l'indirizzo\n📍 Coord: $latCorta, $lonCorta\n🅿️ Tipo: ${parcheggio.tipoParcheggio}\n💰 Costo: $costoTesto"
                        if (marker.isInfoWindowOpen) {
                            marker.closeInfoWindow()
                            marker.showInfoWindow()
                        }
                    }
                }
            }
        }
        binding.mapViewHistory.invalidate()

        if (lista.isNotEmpty()) {
            binding.mapViewHistory.controller.animateTo(GeoPoint(lista[0].latitudine, lista[0].longitudine))
        }
    }

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

    // Finestra informativa personalizzata per i marker della mappa
    class HistoryInfoWindow(mapView: org.osmdroid.views.MapView)
        : org.osmdroid.views.overlay.infowindow.MarkerInfoWindow(it.unibo.lam2026.parkmate.R.layout.marker_info_window, mapView) {

        @Suppress("DEPRECATION")
        override fun onOpen(item: Any?) {
            val marker = item as? org.osmdroid.views.overlay.Marker ?: return

            val txtTitle = mView.findViewById<android.widget.TextView>(it.unibo.lam2026.parkmate.R.id.txtCustomTitle)
            val txtDescription = mView.findViewById<android.widget.TextView>(it.unibo.lam2026.parkmate.R.id.txtCustomDescription)

            // Evidenzia visivamente nel titolo i veicoli non più presenti nel database
            if (marker.title.contains("[ELIMINATO]")) {
                val nomePulito = marker.title.replace(" [ELIMINATO]", "")
                val htmlTesto = "$nomePulito <br> <font color='#D32F2F'><b>❌ VEICOLO ELIMINATO</b></font>"
                txtTitle?.text = android.text.Html.fromHtml(htmlTesto)
            } else {
                txtTitle?.text = marker.title
            }

            txtDescription?.text = marker.snippet

            mView.findViewById<android.widget.Button>(it.unibo.lam2026.parkmate.R.id.btnInfoWindowTermina)?.visibility = android.view.View.GONE
        }
    }
}