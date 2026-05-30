package it.unibo.lam2026.parkmate.model

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PosizioneSalvataDao {

    @Query("SELECT * FROM posizioni_salvate ORDER BY nome ASC")
    fun getAllPosizioni(): Flow<List<PosizioneSalvata>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertPosizione(posizione: PosizioneSalvata)

    // --- NUOVO: Funzione per aggiornare il nome ---
    @Update
    fun updatePosizione(posizione: PosizioneSalvata)

    @Delete
    fun deletePosizione(posizione: PosizioneSalvata)
}