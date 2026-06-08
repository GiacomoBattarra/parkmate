package it.unibo.lam2026.parkmate.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.asLiveData
import it.unibo.lam2026.parkmate.model.SessioneParcheggio
import it.unibo.lam2026.parkmate.model.ParcheggioRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainViewModel(private val repository: ParcheggioRepository) : ViewModel() {

    // Flusso reattivo contenente le sessioni di parcheggio attualmente attive, esposto per il rendering in UI (Mappa)
    val listaParcheggi = repository.parcheggiAttivi.asLiveData()

    // Gestisce l'avvio di una nuova sessione di parcheggio, risolvendo eventuali conflitti con soste precedenti
    fun avviaNuovoParcheggio(nuovaSessione: SessioneParcheggio) {
        viewModelScope.launch(Dispatchers.IO) {

            // Verifica la presenza di una sessione attiva pregressa per il medesimo veicolo
            val vecchiaSessione = repository.getParcheggioAttivoPerVeicolo(nuovaSessione.veicoloNome)

            // Terminazione automatica e storicizzazione della sessione pregressa in caso di conflitto
            if (vecchiaSessione != null) {
                terminaParcheggioSincrono(vecchiaSessione)
            }

            // Persistenza della nuova sessione di sosta
            repository.inserisciParcheggio(nuovaSessione)
        }
    }

    // Interrompe manualmente una sosta in corso recuperandone lo stato dal database
    fun terminaParcheggio(sessionId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val sessione = repository.getParcheggioById(sessionId)
            if (sessione != null) {
                terminaParcheggioSincrono(sessione)
            }
        }
    }

    // Calcolo della tariffa e terminazione sincrona della sessione per garantire coerenza dei dati
    private suspend fun terminaParcheggioSincrono(sessione: SessioneParcheggio) {
        val tempoAttuale = System.currentTimeMillis()
        var costoCalcolato = 0.0

        if (sessione.tariffa > 0.0) {
            if (sessione.tipoParcheggio.contains("Fiss", ignoreCase = true)) {
                costoCalcolato = sessione.tariffa
            } else {
                // Calcolo proporzionale della tariffazione basato sui minuti effettivi di permanenza
                val millisecondiTrascorsi = tempoAttuale - sessione.startTimeStamp

                val minutiTrascorsi = millisecondiTrascorsi.toDouble() / (1000.0 * 60.0)
                val costoAlMinuto = sessione.tariffa / 60.0

                costoCalcolato = minutiTrascorsi * costoAlMinuto

                // Arrotondamento ai due decimali (centesimi di Euro)
                costoCalcolato = Math.round(costoCalcolato * 100.0) / 100.0
            }
        }

        // Consolidamento della chiusura su database
        repository.chiudiParcheggio(sessione.id, tempoAttuale, costoCalcolato)
    }

    // Metodo di utility per popolare l'ambiente con dati mock a scopo di test e dimostrazione
    fun aggiungiParcheggioDiTest() {
        viewModelScope.launch(Dispatchers.IO) {
            val nuovo = SessioneParcheggio(
                veicoloNome = "Mia Auto",
                tipoParcheggio = "Gratis",
                latitudine = 44.4939,
                longitudine = 11.3428,
                startTimeStamp = System.currentTimeMillis(),
                parkingEffortScore = 3
            )
            avviaNuovoParcheggio(nuovo)
        }
    }
}