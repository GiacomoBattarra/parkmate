package it.unibo.lam2026.parkmate.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels // 👇 IMPORTANTE: Aggiunto per condividere il ViewModel dei parcheggi
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import it.unibo.lam2026.parkmate.R
import it.unibo.lam2026.parkmate.databinding.FragmentVehiclesBinding
import it.unibo.lam2026.parkmate.model.AppDatabase
import it.unibo.lam2026.parkmate.model.VeicoloRepository
import it.unibo.lam2026.parkmate.viewmodel.ParcheggioViewModel // 👇 IMPORTANTE: Aggiunto import
import it.unibo.lam2026.parkmate.viewmodel.VeicoliViewModel
import it.unibo.lam2026.parkmate.viewmodel.VeicoliViewModelFactory

class VehiclesFragment : Fragment() {

    private var _binding: FragmentVehiclesBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: VeicoliViewModel
    private lateinit var adapter: VeicoloAdapter

    // 👇 NUOVO: Recuperiamo il ParcheggioViewModel condiviso a livello di Activity 👇
    private val parcheggioViewModel: ParcheggioViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentVehiclesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. Inizializziamo il database e il repository
        val dao = AppDatabase.getDatabase(requireContext()).veicoloDao()
        val repository = VeicoloRepository(dao)

        // 2. Inizializziamo il ViewModel dei Veicoli
        val factory = VeicoliViewModelFactory(repository)
        viewModel = ViewModelProvider(this, factory)[VeicoliViewModel::class.java]

        // 3. Prepariamo il LayoutManager della lista
        binding.recyclerViewVeicoli.layoutManager = LinearLayoutManager(requireContext())

        // 👇 MODIFICA ARCHITETTURALE: Creiamo l'adapter una volta sola (all'inizio vuoto) 👇
        adapter = VeicoloAdapter(
            listaVeicoli = emptyList(),
            onEliminaClick = { veicoloDaEliminare ->
                viewModel.rimuoviVeicolo(veicoloDaEliminare.id)
            },
            onModificaClick = { veicoloDaModificare ->
                mostraDialogModifica(veicoloDaModificare)
            }
        )
        // Attacchiamo subito l'Adapter alla RecyclerView
        binding.recyclerViewVeicoli.adapter = adapter

        // 4. OSSERVIAMO IL DATABASE DEI VEICOLI: aggiorna la lista quando i dati cambiano
        viewModel.listaVeicoli.observe(viewLifecycleOwner) { veicoli ->
            // Usiamo il metodo aggiornaDati per non distruggere e ricreare l'oggetto adapter
            adapter.aggiornaDati(veicoli)
        }

        // 👇 NUOVO: OSSERVIAMO I PARCHEGGI ATTIVI PER ACCENDERE LA "P" 👇
        parcheggioViewModel.parcheggiAttivi.observe(viewLifecycleOwner) { listaParcheggi ->
            // Estraiamo la lista testuale dei veicoli attualmente parcheggiati ("Nome (Tipo)")
            val nomiMacchineParcheggiate = listaParcheggi.map { it.veicoloNome }

            // Passiamo la lista all'adapter che accenderà le P corrispondenti!
            adapter.aggiornaStatoParcheggi(nomiMacchineParcheggiate)
        }

        // 5. Bottone '+' per aggiungere un nuovo veicolo
        binding.fabAggiungiVeicolo.setOnClickListener {
            mostraDialogAggiuntaVeicolo()
        }

        // 6. Diciamo al ViewModel di caricare i dati la prima volta
        viewModel.caricaVeicoli()
    }

    private fun mostraDialogAggiuntaVeicolo() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_aggiungi_veicolo, null)
        val editNome = dialogView.findViewById<android.widget.EditText>(R.id.editNomeVeicolo)
        val spinnerTipo = dialogView.findViewById<android.widget.Spinner>(R.id.spinnerTipoVeicolo)

        val tipiVeicolo = arrayOf("Auto", "Moto", "Bici")
        val spinnerAdapter = android.widget.ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            tipiVeicolo
        )
        spinnerTipo.adapter = spinnerAdapter

        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("Nuovo Veicolo")
            .setView(dialogView)
            .setPositiveButton("Salva") { dialog, _ ->
                val nomeInserito = editNome.text.toString().trim()
                val tipoSelezionato = spinnerTipo.selectedItem.toString()

                if (nomeInserito.isNotEmpty()) {
                    val nuovoVeicolo = it.unibo.lam2026.parkmate.model.Veicolo(
                        nome = nomeInserito,
                        tipo = tipoSelezionato
                    )

                    viewModel.aggiungiVeicolo(nuovoVeicolo)
                    Toast.makeText(requireContext(), "Veicolo salvato!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Inserisci un nome valido!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun mostraDialogModifica(veicoloDaModificare: it.unibo.lam2026.parkmate.model.Veicolo) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_aggiungi_veicolo, null)
        val editNome = dialogView.findViewById<android.widget.EditText>(R.id.editNomeVeicolo)
        val spinnerTipo = dialogView.findViewById<android.widget.Spinner>(R.id.spinnerTipoVeicolo)

        val tipi = arrayOf("Auto", "Moto", "Bici")
        val arrayAdapter = android.widget.ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, tipi)
        spinnerTipo.adapter = arrayAdapter

        editNome.setText(veicoloDaModificare.nome)
        val posizioneTipo = tipi.indexOf(veicoloDaModificare.tipo)
        if (posizioneTipo >= 0) spinnerTipo.setSelection(posizioneTipo)

        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("Modifica Veicolo")
            .setView(dialogView)
            .setPositiveButton("Aggiorna") { _, _ ->
                val nomeInserito = editNome.text.toString().trim()
                val tipoSelezionato = spinnerTipo.selectedItem.toString()

                if (nomeInserito.isNotEmpty()) {
                    val veicoloAggiornato = it.unibo.lam2026.parkmate.model.Veicolo(
                        id = veicoloDaModificare.id,
                        nome = nomeInserito,
                        tipo = tipoSelezionato
                    )

                    viewModel.aggiornaVeicolo(veicoloAggiornato)
                    Toast.makeText(requireContext(), "Veicolo aggiornato!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Annulla", null)
            .show()
    }
}