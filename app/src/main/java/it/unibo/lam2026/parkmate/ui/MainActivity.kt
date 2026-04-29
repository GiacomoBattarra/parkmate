package it.unibo.lam2026.parkmate.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import it.unibo.lam2026.parkmate.databinding.ActivityMainBinding
import it.unibo.lam2026.parkmate.model.AppDatabase
import it.unibo.lam2026.parkmate.model.ParcheggioRepository
import it.unibo.lam2026.parkmate.viewmodel.MainViewModel
import it.unibo.lam2026.parkmate.model.Parcheggio
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import androidx.preference.PreferenceManager

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Configurazione OsmDroid (DEVE stare prima di setContentView)
        Configuration.getInstance().load(
            applicationContext,
            PreferenceManager.getDefaultSharedPreferences(applicationContext)
        )

        // 2. Inizializzazione ViewBinding
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 3. Configurazione iniziale Mappa
        setupMap()

        // 4. Inizializzazione Architettura (DB -> Repository -> ViewModel)
        val database = AppDatabase.getDatabase(applicationContext)
        val repository = ParcheggioRepository(database.parcheggioDao())

        val factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MainViewModel(repository) as T
            }
        }
        viewModel = ViewModelProvider(this, factory).get(MainViewModel::class.java)

        // 5. Osservatore per la parte testuale (per debug)
        viewModel.listaParcheggi.observe(this) { parcheggi ->
            if (parcheggi.isEmpty()) {
                binding.titleText.text = "Nessun parcheggio nel database."
            } else {
                val nomi = parcheggi.joinToString(separator = "\n") { it.nome }
                binding.titleText.text = "Parcheggi salvati:\n$nomi"
            }
        }

        // 6. Osservatore per i Pin sulla Mappa
        // Nota: ho usato 'listaParcheggi', assicurati che nel tuo ViewModel
        // il LiveData si chiami così o sostituisci con 'tuttiIParcheggi'
        viewModel.listaParcheggi.observe(this) { lista ->
            // Puliamo i vecchi marker
            binding.mapView.overlays.clear()

            // Disegniamo i nuovi marker
            lista.forEach { parcheggio ->
                val marker = Marker(binding.mapView)
                marker.position = GeoPoint(parcheggio.latitudine, parcheggio.longitudine)
                marker.title = parcheggio.nome
                marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                binding.mapView.overlays.add(marker)
            }

            // Rinfresca la grafica della mappa
            binding.mapView.invalidate()
        }

        // 7. Listener dei Bottoni
        binding.btnTrova.setOnClickListener {
            viewModel.caricaParcheggi()
        }

        binding.btnAggiungi.setOnClickListener {
            viewModel.aggiungiParcheggioDiTest()
        }

        // Caricamento iniziale
        viewModel.caricaParcheggi()
    }

    // Funzione di supporto per pulire il codice della mappa
    private fun setupMap() {
        val mapController = binding.mapView.controller
        mapController.setZoom(15.0)
        val startPoint = GeoPoint(44.4949, 11.3426) // Bologna
        mapController.setCenter(startPoint)

        binding.mapView.setScrollableAreaLimitLatitude(85.0, -85.0, 0)
        binding.mapView.setScrollableAreaLimitLongitude(-180.0, 180.0, 0)
        binding.mapView.minZoomLevel = 3.0
        binding.mapView.setMultiTouchControls(true)
    }

    // GESTIONE CICLO DI VITA DELLA MAPPA (Obbligatorio per OsmDroid)
    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
    }
}