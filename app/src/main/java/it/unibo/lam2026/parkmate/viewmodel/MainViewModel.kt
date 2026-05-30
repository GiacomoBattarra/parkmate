package it.unibo.lam2026.parkmate.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.asLiveData
import it.unibo.lam2026.parkmate.model.SessioneParcheggio
import it.unibo.lam2026.parkmate.model.ParcheggioRepository
import kotlinx.coroutines.launch

class MainViewModel(private val repository: ParcheggioRepository) : ViewModel() {

    // 1. Il Flow diventa LiveData per il Fragment
    val listaParcheggi = repository.parcheggiAttivi.asLiveData()

    // 2. Aggiunta della funzione per terminare il parcheggio
    fun terminaParcheggio(sessionId: Long) {
        val tempoAttuale = System.currentTimeMillis()
        // Qui calcoliamo il costo (es. 2.50 euro fissi per test)
        val costoCalcolato = 2.50

        viewModelScope.launch {
            repository.chiudiParcheggio(sessionId, tempoAttuale, costoCalcolato)
        }
    }

    fun aggiungiParcheggioDiTest() {
        viewModelScope.launch {
            val nuovo = SessioneParcheggio(
                veicoloNome = "Mia Auto",
                tipoParcheggio = "Libero",
                latitudine = 44.4939,
                longitudine = 11.3428,
                startTimeStamp = System.currentTimeMillis()
            )
            repository.inserisciParcheggio(nuovo)
        }
    }
}