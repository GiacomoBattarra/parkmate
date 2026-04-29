package it.unibo.lam2026.parkmate.model

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ParcheggioRepository(private val dao: ParcheggioDao) {

    suspend fun ottieniParcheggi(): List<Parcheggio> {
        // withContext(Dispatchers.IO) sposta il lavoro su un thread sicuro in background
        return withContext(Dispatchers.IO) {
            dao.getTuttiIParcheggi()
        }
    }

    suspend fun salvaParcheggio(parcheggio: Parcheggio) {
        // Anche l'inserimento lo forziamo sul thread sicuro
        withContext(Dispatchers.IO) {
            dao.inserisciParcheggio(parcheggio)
        }
    }
}