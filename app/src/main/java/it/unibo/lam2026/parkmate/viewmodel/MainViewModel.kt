package it.unibo.lam2026.parkmate.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.asLiveData
import it.unibo.lam2026.parkmate.model.SessioneParcheggio
import it.unibo.lam2026.parkmate.model.ParcheggioRepository
import kotlinx.coroutines.launch

class MainViewModel(private val repository: ParcheggioRepository) : ViewModel() {

    // 1. IL FLOW DIVENTA LIVEDATA
    // Trasformiamo il "tubo" del DB (Flow) nel "canale radio" (LiveData) per il Fragment
    val listaParcheggi = repository.parcheggiAttivi.asLiveData()

    // Non serve più una funzione "caricaParcheggi()", ci pensa il Flow in automatico!

    fun aggiungiParcheggioDiTest() {
        viewModelScope.launch {
            // 2. USIAMO IL NOME CORRETTO: SessioneParcheggio
            val nuovo = SessioneParcheggio(
                veicoloNome = "Mia Auto",
                tipoParcheggio = "Libero",
                latitudine = 44.4939,
                longitudine = 11.3428,
                startTimeStamp = System.currentTimeMillis() // Orario di adesso
            )
            // 3. USIAMO IL METODO DEL REPOSITORY CORRETTO
            repository.inserisciParcheggio(nuovo)
        }
    }
}