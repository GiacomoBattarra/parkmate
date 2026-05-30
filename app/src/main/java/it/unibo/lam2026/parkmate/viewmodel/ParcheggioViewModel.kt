package it.unibo.lam2026.parkmate.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import it.unibo.lam2026.parkmate.model.AppDatabase
import it.unibo.lam2026.parkmate.model.SessioneParcheggio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ParcheggioViewModel(application: Application) : AndroidViewModel(application) {

    // Prendiamo il riferimento al DAO passando per il Database
    private val dao = AppDatabase.getDatabase(application).parcheggioDao()

    // Lo storico mostrerà solo i parcheggi attivi (grazie alla modifica al DAO con archiviato = 0)
    val storicoParcheggi = dao.getStoricoParcheggi().asLiveData()

    // Espone i parcheggi attivi alla Mappa in modo reattivo
    val parcheggiAttivi = dao.getParcheggiAttivi().asLiveData()

    // [NUOVO]: Espone TUTTI i parcheggi (anche quelli nascosti/archiviato = 1) alla schermata delle Statistiche
    val statisticheGlobaliParcheggi = dao.getTuttiIParcheggiPerStats().asLiveData()

    fun salvaParcheggio(nomeVeicolo: String, tipo: String, lat: Double, lon: Double) {
        // Creiamo l'oggetto da salvare
        val nuovaSessione = SessioneParcheggio(
            veicoloNome = nomeVeicolo,
            tipoParcheggio = tipo,
            latitudine = lat,
            longitudine = lon,
            startTimeStamp = System.currentTimeMillis() // Prende l'ora attuale esatta
        )

        // viewModelScope.launch apre un thread in background (Dispatchers.IO)
        viewModelScope.launch(Dispatchers.IO) {
            dao.inserisciParcheggio(nuovaSessione)
        }
    }

    // Funzione per terminare un parcheggio attivo
    fun terminaParcheggio(sessionId: Long) {
        val tempoDiFine = System.currentTimeMillis()

        viewModelScope.launch(Dispatchers.IO) {
            // AGGIUNTO: , 0.0 come parametro del costo
            dao.chiudiParcheggio(sessionId, tempoDiFine, 0.0)
        }
    }

    // Il tasto cancella dello storico ora non elimina più fisicamente dal DB,
    // ma chiama "archiviaParcheggio" per nasconderlo dallo storico ma mantenerlo nelle statistiche!
    fun cancellaParcheggio(sessionId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.archiviaParcheggio(sessionId)
        }
    }
}