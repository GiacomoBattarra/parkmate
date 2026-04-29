package it.unibo.lam2026.parkmate.model

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ParcheggioDao {

    // Niente più suspend!
    @Query("SELECT * FROM tabella_parcheggi")
    fun getTuttiIParcheggi(): List<Parcheggio>

    // Niente più suspend e niente ": Long"!
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun inserisciParcheggio(parcheggio: Parcheggio)
}