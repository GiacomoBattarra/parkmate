package it.unibo.lam2026.parkmate.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import it.unibo.lam2026.parkmate.R
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

        // 3. Prepariamo il LayoutManager della lista
        binding.recyclerViewVeicoli.layoutManager = LinearLayoutManager(requireContext())

        // 4. OSSERVIAMO IL DATABASE: Qui dentro 'veicoli' prende vita!
        viewModel.listaVeicoli.observe(viewLifecycleOwner) { veicoli ->

            // Creiamo l'Adapter usando i dati appena arrivati dal DB
            adapter = VeicoloAdapter(
                listaVeicoli = veicoli,
                onEliminaClick = { veicoloDaEliminare ->
                    viewModel.rimuoviVeicolo(veicoloDaEliminare.id)
                },
                onModificaClick = { veicoloDaModificare ->
                    mostraDialogModifica(veicoloDaModificare)
                }
            )
            // Attacchiamo l'Adapter alla RecyclerView
            binding.recyclerViewVeicoli.adapter = adapter
        }

        // 5. Bottone '+' per aggiungere un nuovo veicolo
        binding.fabAggiungiVeicolo.setOnClickListener {
            mostraDialogAggiuntaVeicolo()
        }

        // 6. Diciamo al ViewModel di caricare i dati la prima volta
        viewModel.caricaVeicoli()
    }
    private fun mostraDialogAggiuntaVeicolo() {
        // 1. Carichiamo (inflate) il layout XML che abbiamo appena creato
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_aggiungi_veicolo, null)
        val editNome = dialogView.findViewById<android.widget.EditText>(R.id.editNomeVeicolo)
        val spinnerTipo = dialogView.findViewById<android.widget.Spinner>(R.id.spinnerTipoVeicolo)

        // 2. Prepariamo i dati per lo Spinner (la tendina)
        val tipiVeicolo = arrayOf("Auto", "Moto", "Bici")
        val spinnerAdapter = android.widget.ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            tipiVeicolo
        )
        spinnerTipo.adapter = spinnerAdapter

        // 3. Costruiamo e mostriamo l'AlertDialog di Google Material
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("Nuovo Veicolo")
            .setView(dialogView)
            .setPositiveButton("Salva") { dialog, _ ->
                // Quando l'utente preme "Salva", recuperiamo i dati
                val nomeInserito = editNome.text.toString().trim()
                val tipoSelezionato = spinnerTipo.selectedItem.toString()

                // ALERT ERRORE COMUNE: Mai fidarsi dell'utente! Controlliamo che il nome non sia vuoto
                if (nomeInserito.isNotEmpty()) {
                    // Creiamo l'oggetto Veicolo (id=0 perché Room lo genera da solo!)
                    val nuovoVeicolo = it.unibo.lam2026.parkmate.model.Veicolo(
                        nome = nomeInserito,
                        tipo = tipoSelezionato
                    )

                    // Lo passiamo al ViewModel che lo salverà nel Database!
                    viewModel.aggiungiVeicolo(nuovoVeicolo)
                    Toast.makeText(requireContext(), "Veicolo salvato!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Inserisci un nome valido!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Annulla", null) // Se preme annulla, si chiude da solo
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

        // Setup dello spinner (Stesse opzioni che usi per aggiungerlo)
        val tipi = arrayOf("Auto", "Moto", "Bici")
        val arrayAdapter = android.widget.ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, tipi)
        spinnerTipo.adapter = arrayAdapter

        // --- PRE-COMPILIAMO I CAMPI ---
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
                    // Creiamo il nuovo oggetto PASSANDO IL VECCHIO ID!
                    val veicoloAggiornato = it.unibo.lam2026.parkmate.model.Veicolo(
                        id = veicoloDaModificare.id, // Fondamentale per dire al DB "sovrascrivi questo"
                        nome = nomeInserito,
                        tipo = tipoSelezionato
                    )

                    viewModel.aggiornaVeicolo(veicoloAggiornato)
                    android.widget.Toast.makeText(requireContext(), "Veicolo aggiornato!", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Annulla", null)
            .show()
    }
}