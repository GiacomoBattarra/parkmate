package it.unibo.lam2026.parkmate.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.unibo.lam2026.parkmate.model.Veicolo
import it.unibo.lam2026.parkmate.model.VeicoloRepository
import kotlinx.coroutines.launch

class VeicoliViewModel(private val repository: VeicoloRepository) : ViewModel() {

    // Il LiveData per far aggiornare l'interfaccia in automatico
    private val _listaVeicoli = MutableLiveData<List<Veicolo>>()
    val listaVeicoli: LiveData<List<Veicolo>> get() = _listaVeicoli

    fun caricaVeicoli() {
        // viewModelScope.launch fa da ponte: lancia una coroutine!
        viewModelScope.launch {
            // Qui dentro siamo nel mondo "suspend"
            val dati = repository.ottieniVeicoli()
            _listaVeicoli.value = dati
        }
    }

    fun aggiungiVeicolo(nuovoVeicolo: Veicolo) {
        viewModelScope.launch {
            repository.salvaVeicolo(nuovoVeicolo)
            // Dopo aver salvato, ricarichiamo la lista aggiornata
            caricaVeicoli()
        }
    }

    fun rimuoviVeicolo(id: Long) {
        viewModelScope.launch {
            repository.eliminaVeicolo(id)
            caricaVeicoli()
        }
    }
}