package it.unibo.lam2026.parkmate.model

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow

class ParcheggioRepository(private val dao: ParcheggioDao) {

    // 1. Variabili Flow (Le tue e del tuo amico)
    val parcheggiAttivi: Flow<List<SessioneParcheggio>> = dao.getParcheggiAttivi()
    val storicoParcheggi: Flow<List<SessioneParcheggio>> = dao.getStoricoParcheggi()

    // [AGGIUNTO] Esponiamo anche i dati per le tue statistiche dal DAO!
    val tuttiIParcheggiPerStats: Flow<List<SessioneParcheggio>> = dao.getTuttiIParcheggiPerStats()

    // 2. Inserimento
    suspend fun inserisciParcheggio(sessione: SessioneParcheggio) {
        // withContext(Dispatchers.IO) protegge l'app, eseguendo l'inserimento in background
        withContext(Dispatchers.IO) {
            dao.inserisciParcheggio(sessione)
        }
    }

    // 3. Chiusura (Codice del TUO AMICO: con il parametro costo)
    suspend fun chiudiParcheggio(sessionId: Long, endTime: Long, costo: Double) {
        withContext(Dispatchers.IO) {
            dao.chiudiParcheggio(sessionId, endTime, costo)
        }
    }

    // 4. Archiviazione (Il TUO codice collegato al DAO per nascondere dallo storico)
    suspend fun archiviaParcheggio(sessionId: Long) {
        withContext(Dispatchers.IO) {
            dao.archiviaParcheggio(sessionId)
        }
    }

    // 5. Eliminazione fisica (Il TUO codice)
    suspend fun eliminaParcheggio(sessionId: Long) {
        withContext(Dispatchers.IO) {
            dao.eliminaParcheggio(sessionId)
        }
    }

    // 6. [NUOVO] Peschiamo il singolo parcheggio per calcolare i costi nel ViewModel
    suspend fun getParcheggioById(sessionId: Long): SessioneParcheggio? {
        return withContext(Dispatchers.IO) {
            dao.getParcheggioById(sessionId)
        }
    }

    // 7. [NUOVO] Recupera un eventuale parcheggio attivo per un veicolo specifico
    suspend fun getParcheggioAttivoPerVeicolo(nomeVeicolo: String): SessioneParcheggio? {
        return withContext(Dispatchers.IO) {
            dao.getParcheggioAttivoPerVeicolo(nomeVeicolo)
        }
    }
}