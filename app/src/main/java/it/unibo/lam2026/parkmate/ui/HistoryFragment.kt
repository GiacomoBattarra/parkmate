package it.unibo.lam2026.parkmate.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.asLiveData
import androidx.recyclerview.widget.LinearLayoutManager
import it.unibo.lam2026.parkmate.databinding.FragmentHistoryBinding
import it.unibo.lam2026.parkmate.viewmodel.ParcheggioViewModel

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    // Usiamo il ViewModel che ci permette di dialogare col Database
    private val viewModel: ParcheggioViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. Configuriamo come deve apparire la lista
        binding.recyclerViewHistory.layoutManager = LinearLayoutManager(requireContext())

        // 2. Osserviamo il Database in modo reattivo
        viewModel.storicoParcheggi.observe(viewLifecycleOwner) { resParcheggi ->

            // 3. Creiamo l'Adapter E passiamo la logica del click nel blocco di parentesi graffe!
            val adapter = HistoryAdapter(resParcheggi) { sessioneDaChiudere ->

                // Questa riga magica scatta solo quando premi "Termina Sosta"
                viewModel.terminaParcheggio(sessioneDaChiudere.id)

            }
            binding.recyclerViewHistory.adapter = adapter
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}