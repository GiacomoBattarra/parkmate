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

    // 1. Inizializziamo il DAO accedendo al database
    private val dao = AppDatabase.getDatabase(application).posizioneSalvataDao()

    // 2. Prepariamo la lista osservabile che la Mappa potrà "ascoltare".
    val posizioniSalvate = dao.getAllPosizioni().asLiveData()

    // 3. La funzione per salvare una nuova posizione
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

    // --- NUOVO: La funzione per aggiornare una posizione esistente ---
    fun aggiornaPosizione(posizione: PosizioneSalvata) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.updatePosizione(posizione)
        }
    }

    // 4. La funzione per eliminare una posizione
    fun eliminaPosizione(posizione: PosizioneSalvata) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deletePosizione(posizione)
        }
    }
}