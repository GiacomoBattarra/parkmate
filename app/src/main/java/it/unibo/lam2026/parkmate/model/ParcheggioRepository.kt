package it.unibo.lam2026.parkmate.model

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow

class ParcheggioRepository(private val dao: ParcheggioDao) {

    val parcheggiAttivi: Flow<List<SessioneParcheggio>> = dao.getParcheggiAttivi()
    val storicoParcheggi: Flow<List<SessioneParcheggio>> = dao.getStoricoParcheggi()

    // Flusso completo per l'elaborazione delle statistiche globali dell'utente
    val tuttiIParcheggiPerStats: Flow<List<SessioneParcheggio>> = dao.getTuttiIParcheggiPerStats()

    suspend fun inserisciParcheggio(sessione: SessioneParcheggio) {
        // Assicura l'esecuzione sicura in background sul thread dedicato alle operazioni di I/O
        withContext(Dispatchers.IO) {
            dao.inserisciParcheggio(sessione)
        }
    }

    // Termina la sessione di parcheggio registrando l'ora di fine e il costo calcolato
    suspend fun chiudiParcheggio(sessionId: Long, endTime: Long, costo: Double) {
        withContext(Dispatchers.IO) {
            dao.chiudiParcheggio(sessionId, endTime, costo)
        }
    }

    // Nasconde il parcheggio dalla visualizzazione senza eliminarlo dal DB (soft delete)
    suspend fun archiviaParcheggio(sessionId: Long) {
        withContext(Dispatchers.IO) {
            dao.archiviaParcheggio(sessionId)
        }
    }

    // Rimozione fisica e permanente del record dal database (hard delete)
    suspend fun eliminaParcheggio(sessionId: Long) {
        withContext(Dispatchers.IO) {
            dao.eliminaParcheggio(sessionId)
        }
    }

    // Recupera i dettagli di una singola sessione per calcoli o validazioni pre-chiusura
    suspend fun getParcheggioById(sessionId: Long): SessioneParcheggio? {
        return withContext(Dispatchers.IO) {
            dao.getParcheggioById(sessionId)
        }
    }

    // Controlla se un veicolo ha già una sosta in corso per prevenire duplicati
    suspend fun getParcheggioAttivoPerVeicolo(nomeVeicolo: String): SessioneParcheggio? {
        return withContext(Dispatchers.IO) {
            dao.getParcheggioAttivoPerVeicolo(nomeVeicolo)
        }
    }
}