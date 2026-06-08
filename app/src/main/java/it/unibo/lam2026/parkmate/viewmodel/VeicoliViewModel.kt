package it.unibo.lam2026.parkmate.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.unibo.lam2026.parkmate.model.Veicolo
import it.unibo.lam2026.parkmate.model.VeicoloRepository
import kotlinx.coroutines.launch

class VeicoliViewModel(private val repository: VeicoloRepository) : ViewModel() {

    // Backing property per l'incapsulamento dello stato: espone un flusso reattivo di sola lettura alla UI
    private val _listaVeicoli = MutableLiveData<List<Veicolo>>()
    val listaVeicoli: LiveData<List<Veicolo>> get() = _listaVeicoli

    // Recupera l'anagrafica dei veicoli dal database delegando l'operazione in modo asincrono
    fun caricaVeicoli() {
        viewModelScope.launch {
            val dati = repository.ottieniVeicoli()
            _listaVeicoli.value = dati
        }
    }

    // Gestisce la persistenza di un nuovo veicolo e la successiva sincronizzazione dello stato locale
    fun aggiungiVeicolo(nuovoVeicolo: Veicolo) {
        viewModelScope.launch {
            repository.salvaVeicolo(nuovoVeicolo)
            caricaVeicoli()
        }
    }

    // Propaga le modifiche anagrafiche al layer dati e aggiorna la View
    fun aggiornaVeicolo(veicoloModificato: Veicolo) {
        viewModelScope.launch {
            repository.aggiornaVeicolo(veicoloModificato)
            caricaVeicoli()
        }
    }

    // Rimuove l'entità dal database e richiede un refresh della lista per allineare l'interfaccia
    fun rimuoviVeicolo(id: Long) {
        viewModelScope.launch {
            repository.eliminaVeicolo(id)
            caricaVeicoli()
        }
    }
}