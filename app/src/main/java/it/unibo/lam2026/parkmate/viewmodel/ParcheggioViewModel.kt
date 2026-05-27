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
    val storicoParcheggi = dao.getStoricoParcheggi().asLiveData()
    // Questa NON è suspend, quindi puoi chiamarla dal bottone nel Fragment!
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
}