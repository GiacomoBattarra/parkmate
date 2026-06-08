package it.unibo.lam2026.parkmate.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import it.unibo.lam2026.parkmate.model.AppDatabase
import it.unibo.lam2026.parkmate.model.PosizioneSalvata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PosizioniSalvateViewModel(application: Application) : AndroidViewModel(application) {

    // Inizializzazione del Data Access Object per la gestione dei luoghi preferiti
    private val dao = AppDatabase.getDatabase(application).posizioneSalvataDao()

    // Flusso reattivo delle posizioni salvate, esposto per l'aggiornamento real-time della View (Mappa)
    val posizioniSalvate = dao.getAllPosizioni().asLiveData()

    // Persistenza asincrona di un nuovo marcatore spaziale (luogo preferito)
    fun salvaNuovaPosizione(nomeLuogo: String, lat: Double, lng: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            val nuovaPosizione = PosizioneSalvata(
                nome = nomeLuogo,
                latitudine = lat,
                longitudine = lng
            )
            dao.insertPosizione(nuovaPosizione)
        }
    }

    // Aggiornamento dei metadati o delle coordinate di una posizione preesistente
    fun aggiornaPosizione(posizione: PosizioneSalvata) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.updatePosizione(posizione)
        }
    }

    // Rimozione permanente di una posizione salvata dal database locale
    fun eliminaPosizione(posizione: PosizioneSalvata) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deletePosizione(posizione)
        }
    }
}