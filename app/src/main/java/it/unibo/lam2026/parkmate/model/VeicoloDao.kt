package it.unibo.lam2026.parkmate.model

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface VeicoloDao {

    // Ottenere tutti i veicoli per mostrarli nella lista
    @Query("SELECT * FROM tabella_veicoli")
    fun getTuttiIVeicoli(): List<Veicolo>

    // Salvare un nuovo veicolo
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun inserisciVeicolo(veicolo: Veicolo)

    // Eliminare un veicolo (richiesto dalle specifiche del progetto!)
    @Query("DELETE FROM tabella_veicoli WHERE id = :veicoloId")
    fun eliminaVeicolo(veicoloId: Long)
}