package it.unibo.lam2026.parkmate.model

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface VeicoloDao {

    // Recupera l'elenco di tutti i veicoli registrati per popolare l'interfaccia utente
    @Query("SELECT * FROM tabella_veicoli")
    fun getTuttiIVeicoli(): List<Veicolo>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun inserisciVeicolo(veicolo: Veicolo)

    // Rimuove permanentemente il veicolo dal database
    @Query("DELETE FROM tabella_veicoli WHERE id = :veicoloId")
    fun eliminaVeicolo(veicoloId: Long)

    // Aggiorna i dati di un veicolo esistente
    @Update
    fun aggiornaVeicolo(veicolo: Veicolo)
}