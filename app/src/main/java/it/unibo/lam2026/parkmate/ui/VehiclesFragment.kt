package it.unibo.lam2026.parkmate.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import it.unibo.lam2026.parkmate.databinding.FragmentVehiclesBinding
import it.unibo.lam2026.parkmate.model.AppDatabase
import it.unibo.lam2026.parkmate.model.VeicoloRepository
import it.unibo.lam2026.parkmate.viewmodel.VeicoliViewModel
import it.unibo.lam2026.parkmate.viewmodel.VeicoliViewModelFactory // Creeremo questa Factory a breve

class VehiclesFragment : Fragment() {

    private var _binding: FragmentVehiclesBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: VeicoliViewModel
    private lateinit var adapter: VeicoloAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentVehiclesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. Inizializziamo il database e il repository
        val dao = AppDatabase.getDatabase(requireContext()).veicoloDao()
        val repository = VeicoloRepository(dao)

        // 2. Inizializziamo il ViewModel
        val factory = VeicoliViewModelFactory(repository)
        viewModel = ViewModelProvider(this, factory)[VeicoliViewModel::class.java]

        // 3. Prepariamo la RecyclerView (la lista)
        adapter = VeicoloAdapter(emptyList()) { veicoloEliminato ->

            // Questo blocco scatta quando l'utente preme il cestino sull'Adapter!
            viewModel.rimuoviVeicolo(veicoloEliminato.id)
            Toast.makeText(requireContext(), "Veicolo eliminato", Toast.LENGTH_SHORT).show()
        }

        binding.recyclerViewVeicoli.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewVeicoli.adapter = adapter

        // 4. Osserviamo il database: quando i veicoli cambiano, aggiorniamo la lista visiva!
        viewModel.listaVeicoli.observe(viewLifecycleOwner) { veicoli ->
            adapter.aggiornaDati(veicoli)
        }

        // 5. Bottone '+' per aggiungere un nuovo veicolo
        binding.fabAggiungiVeicolo.setOnClickListener {
            Toast.makeText(requireContext(), "Qui inseriremo il Dialog per aggiungere un veicolo!", Toast.LENGTH_SHORT).show()
        }

        // 6. Diciamo al ViewModel di caricare i dati la prima volta
        viewModel.caricaVeicoli()
    }
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}