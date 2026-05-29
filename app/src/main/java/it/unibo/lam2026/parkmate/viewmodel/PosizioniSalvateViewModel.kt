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
    // Usiamo asLiveData() per convertire il Flow del DAO in un formato comodo per la UI
    val posizioniSalvate = dao.getAllPosizioni().asLiveData()

    // 3. La funzione per salvare una nuova posizione
    fun salvaNuovaPosizione(nomeLuogo: String, lat: Double, lng: Double) {

        // viewModelScope.launch fa partire un processo in background!
        viewModelScope.launch(Dispatchers.IO) {
            // Creiamo l'oggetto da salvare
            val nuovaPosizione = PosizioneSalvata(
                nome = nomeLuogo,
                latitudine = lat,
                longitudine = lng
            )

            // Diciamo al DAO di inserirlo nel Database
            dao.insertPosizione(nuovaPosizione)
        }
    }

    // 4. (Opzionale ma utile) La funzione per eliminare una posizione
    fun eliminaPosizione(posizione: PosizioneSalvata) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deletePosizione(posizione)
        }
    }
}