package it.unibo.lam2026.parkmate.model

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ParcheggioRepository(private val dao: ParcheggioDao) {

    suspend fun ottieniParcheggi(): List<Parcheggio> {
       return withContext(Dispatchers.IO) {
            dao.getTuttiIParcheggi()
       }
    }

    suspend fun salvaParcheggio(parcheggio: Parcheggio) {
        withContext(Dispatchers.IO) {
            dao.inserisciParcheggio(parcheggio)
        }
    }
}