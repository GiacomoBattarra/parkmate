package it.unibo.lam2026.parkmate.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import it.unibo.lam2026.parkmate.model.AppDatabase
import it.unibo.lam2026.parkmate.model.SessioneParcheggio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ParcheggioViewModel(application: Application) : AndroidViewModel(application) {

    // Prendiamo il riferimento al DAO passando per il Database
    private val dao = AppDatabase.getDatabase(application).parcheggioDao()
    val storicoParcheggi = dao.getStoricoParcheggi().asLiveData()
    // Espone i parcheggi attivi alla Mappa in modo reattivo
    val parcheggiAttivi = dao.getParcheggiAttivi().asLiveData()
    fun salvaParcheggio(nomeVeicolo: String, tipo: String, lat: Double, lon: Double) {

        // Creiamo l'oggetto da salvare
        val nuovaSessione = SessioneParcheggio(
            veicoloNome = nomeVeicolo,
            tipoParcheggio = tipo,
            latitudine = lat,
            longitudine = lon,
            startTimeStamp = System.currentTimeMillis() // Prende l'ora attuale esatta
        )

        // ECCO LA MAGIA: viewModelScope.launch apre un thread in background (Dispatchers.IO)
        // Tutto quello che c'è qui dentro non blocca l'app!
        viewModelScope.launch(Dispatchers.IO) {

            // Qui DENTRO possiamo chiamare tranquillamente la funzione suspend!
            dao.inserisciParcheggio(nuovaSessione)

        }
    }

    // Funzione per terminare un parcheggio attivo
    fun terminaParcheggio(sessionId: Long) {

        // Calcoliamo il timestamp esatto di questo momento
        val tempoDiFine = System.currentTimeMillis()

        // Lanciamo la coroutine nel thread di background (Dispatchers.IO)
        viewModelScope.launch(Dispatchers.IO) {

            // Chiamiamo il metodo del DAO per aggiornare il record nel DB
            dao.chiudiParcheggio(sessionId, tempoDiFine)

        }
    }

    fun cancellaParcheggio(sessionId: Long) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            dao.eliminaParcheggio(sessionId)
        }
    }
    suspend fun eliminaParcheggio(sessionId: Long) {
        withContext(Dispatchers.IO) {
            dao.eliminaParcheggio(sessionId)
        }
    }
}