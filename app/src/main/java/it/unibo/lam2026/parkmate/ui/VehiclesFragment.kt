package it.unibo.lam2026.parkmate.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import it.unibo.lam2026.parkmate.R
import it.unibo.lam2026.parkmate.databinding.FragmentVehiclesBinding
import it.unibo.lam2026.parkmate.model.AppDatabase
import it.unibo.lam2026.parkmate.model.VeicoloRepository
import it.unibo.lam2026.parkmate.viewmodel.ParcheggioViewModel
import it.unibo.lam2026.parkmate.viewmodel.VeicoliViewModel
import it.unibo.lam2026.parkmate.viewmodel.VeicoliViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VehiclesFragment : Fragment() {

    private var _binding: FragmentVehiclesBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: VeicoliViewModel
    private lateinit var adapter: VeicoloAdapter

    private val parcheggioViewModel: ParcheggioViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentVehiclesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val dao = AppDatabase.getDatabase(requireContext()).veicoloDao()
        val repository = VeicoloRepository(dao)

        val factory = VeicoliViewModelFactory(repository)
        viewModel = ViewModelProvider(this, factory)[VeicoliViewModel::class.java]

        binding.recyclerViewVeicoli.layoutManager = LinearLayoutManager(requireContext())

        adapter = VeicoloAdapter(
            listaVeicoli = emptyList(),
            onEliminaClick = { veicoloDaEliminare ->
                mostraDialogEliminazione(veicoloDaEliminare)
            },
            onModificaClick = { veicoloDaModificare ->
                mostraDialogModifica(veicoloDaModificare)
            },
            onParcheggioAttivoClick = { veicoloParcheggiato ->
                mostraDettagliSosta(veicoloParcheggiato)
            }
        )

        binding.recyclerViewVeicoli.adapter = adapter

        viewModel.listaVeicoli.observe(viewLifecycleOwner) { veicoli ->
            adapter.aggiornaDati(veicoli)
        }

        parcheggioViewModel.parcheggiAttivi.observe(viewLifecycleOwner) { listaParcheggi ->
            val nomiMacchineParcheggiate = listaParcheggi.map { it.veicoloNome }
            adapter.aggiornaStatoParcheggi(nomiMacchineParcheggiate)
        }

        binding.fabAggiungiVeicolo.setOnClickListener {
            mostraDialogAggiuntaVeicolo()
        }

        viewModel.caricaVeicoli()
    }

    // Flusso di conferma per l'eliminazione del veicolo, con gestione a cascata delle soste attive e storicizzazione
    private fun mostraDialogEliminazione(veicolo: it.unibo.lam2026.parkmate.model.Veicolo) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("Elimina Veicolo")
            .setMessage("Sei sicuro di voler eliminare '${veicolo.nome}'?\n\nEventuali soste in corso verranno interrotte, ma i parcheggi passati rimarranno visibili nello storico con un'etichetta speciale.")
            .setPositiveButton("Sì, elimina") { _, _ ->
                val nomeNelDatabase = "${veicolo.nome} (${veicolo.tipo})"
                parcheggioViewModel.gestisciEliminazioneVeicolo(nomeNelDatabase)
                viewModel.rimuoviVeicolo(veicolo.id)
                Toast.makeText(requireContext(), "Veicolo eliminato correttamente!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    // Recupero e presentazione dei dettagli spaziali e temporali relativi a una sessione di parcheggio in corso
    private fun mostraDettagliSosta(veicolo: it.unibo.lam2026.parkmate.model.Veicolo) {
        val parcheggiInCorso = parcheggioViewModel.parcheggiAttivi.value ?: return
        val nomeNelDatabase = "${veicolo.nome} (${veicolo.tipo})"
        val sosta = parcheggiInCorso.find { it.veicoloNome == nomeNelDatabase }

        if (sosta != null) {
            val formattaData = java.text.SimpleDateFormat("dd/MM/yyyy - HH:mm", java.util.Locale.getDefault())
            val data = formattaData.format(java.util.Date(sosta.startTimeStamp))

            // Risoluzione asincrona delle coordinate in indirizzo fisico (Reverse Geocoding)
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                var indirizzoTesto = ""
                try {
                    val geocoder = android.location.Geocoder(requireContext(), java.util.Locale.getDefault())
                    val indirizzi = geocoder.getFromLocation(sosta.latitudine, sosta.longitudine, 1)

                    if (!indirizzi.isNullOrEmpty()) {
                        val addr = indirizzi[0]
                        val via = addr.thoroughfare ?: ""
                        val civico = addr.subThoroughfare ?: ""
                        val citta = addr.locality ?: ""

                        indirizzoTesto = if (via.isNotEmpty() && citta.isNotEmpty()) {
                            if (civico.isNotEmpty()) "$via $civico, $citta" else "$via, $citta"
                        } else {
                            addr.getAddressLine(0) ?: "Indirizzo sconosciuto"
                        }
                    } else {
                        val latCorta = String.format(java.util.Locale.getDefault(), "%.4f", sosta.latitudine)
                        val lonCorta = String.format(java.util.Locale.getDefault(), "%.4f", sosta.longitudine)
                        indirizzoTesto = "Coord: $latCorta, $lonCorta"
                    }
                } catch (e: Exception) {
                    val latCorta = String.format(java.util.Locale.getDefault(), "%.4f", sosta.latitudine)
                    val lonCorta = String.format(java.util.Locale.getDefault(), "%.4f", sosta.longitudine)
                    indirizzoTesto = "Non riesco a caricare l'indirizzo\nCoord: $latCorta, $lonCorta"
                }

                // Sincronizzazione sul Main Thread per il rendering del dialog con le informazioni geografiche
                withContext(Dispatchers.Main) {
                    com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                        .setTitle("🅿️ Sosta in corso")
                        .setMessage("Veicolo: ${veicolo.nome}\nIniziata il: $data\nTariffa: ${sosta.tipoParcheggio}\nPosizione: 📍 $indirizzoTesto\n\nPer maggiori dettagli o per terminare la sosta, recati nella sezione Mappa o nello Storico.")
                        .setPositiveButton("Chiudi", null)
                        .show()
                }
            }
        }
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