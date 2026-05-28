package it.unibo.lam2026.parkmate.model

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow

class ParcheggioRepository(private val dao: ParcheggioDao) {

    // Dichiarati come Flow, devono corrispondere a quanto ritorna il DAO
    val parcheggiAttivi: Flow<List<SessioneParcheggio>> = dao.getParcheggiAttivi()
    val storicoParcheggi: Flow<List<SessioneParcheggio>> = dao.getStoricoParcheggi()

    suspend fun inserisciParcheggio(sessione: SessioneParcheggio) {
        withContext(Dispatchers.IO) {
            dao.inserisciParcheggio(sessione)
        }
    }
    suspend fun eliminaParcheggio(sessionId: Long) {
        withContext(Dispatchers.IO) {
            dao.eliminaParcheggio(sessionId)
        }
    }
    suspend fun chiudiParcheggio(sessionId: Long, endTime: Long) {
        withContext(Dispatchers.IO) {
            dao.chiudiParcheggio(sessionId, endTime)
        }
    }
}