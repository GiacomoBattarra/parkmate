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

        // 1. Configuriamo come deve apparire la lista (layout lineare classico)
        binding.recyclerViewHistory.layoutManager = LinearLayoutManager(requireContext())

        // 2. Osserviamo il Database in modo reattivo
        viewModel.storicoParcheggi.observe(viewLifecycleOwner) { resParcheggi ->
            // resParcheggi contiene la lista aggiornata dei risultati dal DB
            // Creiamo un nuovo adapter con questi dati e lo assegniamo alla RecyclerView
            val adapter = ParcheggiAdapter(resParcheggi)
            binding.recyclerViewHistory.adapter = adapter
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}