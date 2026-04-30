package it.unibo.lam2026.parkmate.model

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class VeicoloRepository(private val dao: VeicoloDao) {

    suspend fun ottieniVeicoli(): List<Veicolo> {
        // Sposta il lavoro su un thread sicuro in background[cite: 2]
        return withContext(Dispatchers.IO) {
            dao.getTuttiIVeicoli()
        }
    }

    suspend fun salvaVeicolo(veicolo: Veicolo) {
        // Forza l'inserimento sul thread sicuro[cite: 2]
        withContext(Dispatchers.IO) {
            dao.inserisciVeicolo(veicolo)
        }
    }

    suspend fun eliminaVeicolo(veicoloId: Long) {
        // Forza l'eliminazione sul thread sicuro[cite: 2]
        withContext(Dispatchers.IO) {
            dao.eliminaVeicolo(veicoloId)
        }
    }
}