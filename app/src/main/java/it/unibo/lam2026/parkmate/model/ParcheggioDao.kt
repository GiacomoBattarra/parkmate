package it.unibo.lam2026.parkmate.model

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ParcheggioDao {
    @Query("SELECT * FROM tabella_sessioni_parcheggio WHERE isAttivo = 1")
    fun getParcheggiAttivi(): Flow<List<SessioneParcheggio>>

    @Query("SELECT * FROM tabella_sessioni_parcheggio WHERE isAttivo = 0")
    fun getStoricoParcheggi(): Flow<List<SessioneParcheggio>>

    // RIMOSSO "suspend" e rimosso ": Long"
    @Insert
    fun inserisciParcheggio(sessione: SessioneParcheggio)

    // RIMOSSO "suspend" e rimosso ": Int"
    @Query("UPDATE tabella_sessioni_parcheggio SET isAttivo = 0, endTimeStamp = :endTime, costoTotale = :costo WHERE id = :sessionId")
    fun chiudiParcheggio(sessionId: Long, endTime: Long, costo: Double)
}