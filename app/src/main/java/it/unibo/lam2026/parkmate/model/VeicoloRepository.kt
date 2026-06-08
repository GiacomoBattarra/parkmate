package it.unibo.lam2026.parkmate.model

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class VeicoloRepository(private val dao: VeicoloDao) {

    // Recupera l'elenco dei veicoli delegando l'operazione al thread I/O
    suspend fun ottieniVeicoli(): List<Veicolo> {
        return withContext(Dispatchers.IO) {
            dao.getTuttiIVeicoli()
        }
    }

    // Salva un nuovo veicolo nel database
    suspend fun salvaVeicolo(veicolo: Veicolo) {
        withContext(Dispatchers.IO) {
            dao.inserisciVeicolo(veicolo)
        }
    }

    // Sovrascrive i dati di un veicolo precedentemente salvato
    suspend fun aggiornaVeicolo(veicolo: Veicolo) {
        withContext(Dispatchers.IO) {
            dao.aggiornaVeicolo(veicolo)
        }
    }

    // Rimuove permanentemente un veicolo dal database
    suspend fun eliminaVeicolo(veicoloId: Long) {
        withContext(Dispatchers.IO) {
            dao.eliminaVeicolo(veicoloId)
        }
    }
}