package it.unibo.lam2026.parkmate.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.unibo.lam2026.parkmate.model.ParcheggioRepository
import it.unibo.lam2026.parkmate.model.SessioneParcheggio
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(private val repository: ParcheggioRepository) : ViewModel() {

    val storicoSoste: StateFlow<List<SessioneParcheggio>> = repository.storicoParcheggi
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Funzione per terminare una sosta attiva
    fun terminaParcheggio(sessionId: Long) {
        // Calcoliamo il tempo attuale in millisecondi
        val tempoDiFine = System.currentTimeMillis()

        // Apriamo la coroutine per fare l'aggiornamento in background senza bloccare l'app
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            // AGGIUNTO: , 0.0 come parametro del costo
            repository.chiudiParcheggio(sessionId, tempoDiFine, 0.0)
        }
    }
}