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

class MapFragment : Fragment() {

    // 1. Setup del ViewBinding specifico per i Fragment (evita memory leaks)
    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 2. Configurazione OsmDroid: va fatta PRIMA di creare l'interfaccia!
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
        // 3. Ora che la view esiste, possiamo impostare la mappa
        setupMap()
    }

    private fun setupMap() {
        val mapController = binding.mapView.controller
        mapController.setZoom(15.0)
        val startPoint = GeoPoint(44.4949, 11.3426) // Coordinate di Bologna
        mapController.setCenter(startPoint)

        binding.mapView.minZoomLevel = 3.0
        binding.mapView.setMultiTouchControls(true)
    }

    // 4. Gestione OBBLIGATORIA del ciclo di vita della mappa di OsmDroid
    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
    }

    // 5. Pulizia della memoria quando il Fragment viene distrutto
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}