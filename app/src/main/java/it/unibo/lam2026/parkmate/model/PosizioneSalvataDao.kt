package it.unibo.lam2026.parkmate.model

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PosizioneSalvataDao {

    @Query("SELECT * FROM posizioni_salvate ORDER BY nome ASC")
    fun getAllPosizioni(): Flow<List<PosizioneSalvata>>

    // 1. Tolto 'suspend' e tolto ': Long'
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertPosizione(posizione: PosizioneSalvata)

    // 2. Tolto 'suspend' e tolto ': Int'
    @Delete
    fun deletePosizione(posizione: PosizioneSalvata)
}