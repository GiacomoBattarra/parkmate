package it.unibo.lam2026.parkmate.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.unibo.lam2026.parkmate.model.Parcheggio
import it.unibo.lam2026.parkmate.model.ParcheggioRepository
import kotlinx.coroutines.launch

// Ora il ViewModel richiede il Repository nel costruttore!
class MainViewModel(private val repository: ParcheggioRepository) : ViewModel() {

    private val _listaParcheggi = MutableLiveData<List<Parcheggio>>()
    val listaParcheggi: LiveData<List<Parcheggio>> get() = _listaParcheggi

    fun caricaParcheggi() {
        // viewModelScope.launch sposta il lavoro in background!
        viewModelScope.launch {
            val dati = repository.ottieniParcheggi()
            _listaParcheggi.value = dati // LiveData aggiorna la UI in automatico
        }
    }

    fun aggiungiParcheggioDiTest() {
        viewModelScope.launch {
            val nuovo = Parcheggio(
                id = System.currentTimeMillis().toString(), // Un ID univoco basato sull'orario
                nome = "Parcheggio Piazza Maggiore",
                postiDisponibili = 20,
                tariffaOraria = 2.50,
                latitudine = 44.4939, // Coordinate reali di Bologna
                longitudine = 11.3428
            )
            repository.salvaParcheggio(nuovo)
            caricaParcheggi() // Rinfresca la lista
        }
    }
}