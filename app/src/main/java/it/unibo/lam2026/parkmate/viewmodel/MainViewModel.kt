package it.unibo.lam2026.parkmate.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.asLiveData
import it.unibo.lam2026.parkmate.model.SessioneParcheggio
import it.unibo.lam2026.parkmate.model.ParcheggioRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainViewModel(private val repository: ParcheggioRepository) : ViewModel() {

    // 1. La lista reattiva per la mappa
    val listaParcheggi = repository.parcheggiAttivi.asLiveData()

    // ------------------------------------------------------------------------
    // [NUOVA FUNZIONE] DA USARE QUANDO SALVI UN NUOVO PARCHEGGIO DAL BOTTOM SHEET
    // ------------------------------------------------------------------------
    fun avviaNuovoParcheggio(nuovaSessione: SessioneParcheggio) {
        viewModelScope.launch(Dispatchers.IO) {

            // 1. Controlliamo se questa macchina è già parcheggiata da qualche altra parte
            val vecchiaSessione = repository.getParcheggioAttivoPerVeicolo(nuovaSessione.veicoloNome)

            // 2. Se sì, la chiudiamo in automatico salvando la tariffa!
            if (vecchiaSessione != null) {
                terminaParcheggioSincrono(vecchiaSessione)
            }

            // 3. Ora che la vecchia sosta è chiusa, inseriamo quella nuova pulita
            repository.inserisciParcheggio(nuovaSessione)
        }
    }

    // ------------------------------------------------------------------------
    // FUNZIONE STANDARD (Chiamata quando premi "Termina Sosta" sulla mappa)
    // ------------------------------------------------------------------------
    fun terminaParcheggio(sessionId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val sessione = repository.getParcheggioById(sessionId)
            if (sessione != null) {
                terminaParcheggioSincrono(sessione)
            }
        }
    }

    // ------------------------------------------------------------------------
// LOGICA MATEMATICA (Usata da entrambe le funzioni qui sopra per non ripetere codice)
// ------------------------------------------------------------------------
    private suspend fun terminaParcheggioSincrono(sessione: SessioneParcheggio) {
        val tempoAttuale = System.currentTimeMillis()
        var costoCalcolato = 0.0

        if (sessione.tariffa > 0.0) {
            if (sessione.tipoParcheggio.contains("Fiss", ignoreCase = true)) {
                costoCalcolato = sessione.tariffa
            } else {
                // --- NUOVA MATEMATICA AL MINUTO ---
                val millisecondiTrascorsi = tempoAttuale - sessione.startTimeStamp

                // 1. Calcoliamo i minuti esatti con i decimali
                val minutiTrascorsi = millisecondiTrascorsi.toDouble() / (1000.0 * 60.0)

                // 2. Troviamo il costo per singolo minuto
                val costoAlMinuto = sessione.tariffa / 60.0

                // 3. Moltiplichiamo per ottenere il costo esatto
                costoCalcolato = minutiTrascorsi * costoAlMinuto

                // Arrotondamento ai centesimi (es. 1.45€)
                costoCalcolato = Math.round(costoCalcolato * 100.0) / 100.0
            }
        }

        // Salviamo nel DB!
        repository.chiudiParcheggio(sessione.id, tempoAttuale, costoCalcolato)
    }

    // (La tua funzione di test)
    fun aggiungiParcheggioDiTest() {
        viewModelScope.launch(Dispatchers.IO) {
            val nuovo = SessioneParcheggio(
                veicoloNome = "Mia Auto",
                tipoParcheggio = "Libero",
                latitudine = 44.4939,
                longitudine = 11.3428,
                startTimeStamp = System.currentTimeMillis()
            )
            avviaNuovoParcheggio(nuovo) // Usiamo la nuova funzione anche qui!
        }
    }
}