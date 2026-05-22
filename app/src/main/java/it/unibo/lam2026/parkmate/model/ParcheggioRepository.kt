package it.unibo.lam2026.parkmate.model

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow

class ParcheggioRepository(private val dao: ParcheggioDao) {

    val parcheggiAttivi: Flow<List<SessioneParcheggio>> = dao.getParcheggiAttivi()
    val storicoParcheggi: Flow<List<SessioneParcheggio>> = dao.getStoricoParcheggi()

    // Questa rimane suspend per il ViewModel, ma sposta il lavoro su IO internamente
    suspend fun inserisciParcheggio(sessione: SessioneParcheggio) {
        withContext(Dispatchers.IO) {
            dao.inserisciParcheggio(sessione)
        }
    }

    suspend fun chiudiParcheggio(sessionId: Long, endTime: Long) {
        withContext(Dispatchers.IO) {
            dao.chiudiParcheggio(sessionId, endTime)
        }
    }
}