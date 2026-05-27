package it.unibo.lam2026.parkmate.model

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ParcheggioDao {

    // 1. Lettura Parcheggi Attivi (per la Mappa)
    // Usiamo Flow: è un "tubo" diretto tra database e UI. Se un parcheggio viene aggiunto,
    // la mappa si aggiorna da sola senza che tu debba ricaricarla!
    @Query("SELECT * FROM tabella_sessioni_parcheggio WHERE isAttivo = 1")
    fun getParcheggiAttivi(): Flow<List<SessioneParcheggio>>

    // 2. Lettura Storico (per la lista History)
    @Query("SELECT * FROM tabella_sessioni_parcheggio ORDER BY startTimeStamp DESC")
    fun getStoricoParcheggi(): Flow<List<SessioneParcheggio>>

    // 3. Scrittura (DEVE essere suspend per non bloccare l'interfaccia!)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun inserisciParcheggio(sessione: SessioneParcheggio)

    // 4. Aggiornamento (quando chiudi un parcheggio attivo)
    @Query("UPDATE tabella_sessioni_parcheggio SET isAttivo = 0, endTimeStamp = :endTime WHERE id = :sessionId")
    fun chiudiParcheggio(sessionId: Long, endTime: Long)

    // 5. Eliminazione fisica dal database
    @Query("DELETE FROM tabella_sessioni_parcheggio WHERE id = :sessionId")
    fun eliminaParcheggio(sessionId: Long)
}