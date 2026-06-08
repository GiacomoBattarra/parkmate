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

    // Gestisce la terminazione di una sessione di parcheggio attiva e il cleanup dei processi correlati
    fun terminaParcheggio(sessionId: Long, veicoloNome: String, context: android.content.Context) {
        val tempoDiFine = System.currentTimeMillis()

        // Esecuzione asincrona (Thread I/O) per la persistenza dello stato aggiornato sul database
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            repository.chiudiParcheggio(sessionId, tempoDiFine, 0.0)

            // Deregistrazione del WorkManager per interrompere il ciclo di notifiche periodiche (promemoria sosta)
            androidx.work.WorkManager.getInstance(context).cancelAllWorkByTag("SESSION_$veicoloNome")
        }
    }
}