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

    // 2. MODIFICATO: Lo Storico ora mostra solo i parcheggi NON archiviati (archiviato = 0)
    @Query("SELECT * FROM tabella_sessioni_parcheggio WHERE archiviato = 0 ORDER BY startTimeStamp DESC")
    fun getStoricoParcheggi(): Flow<List<SessioneParcheggio>>

    // [NUOVO]: Le statistiche leggono tutto il database globale, anche i cancellati!
    @Query("SELECT * FROM tabella_sessioni_parcheggio ORDER BY startTimeStamp DESC")
    fun getTuttiIParcheggiPerStats(): Flow<List<SessioneParcheggio>>

    // 3. Scrittura
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun inserisciParcheggio(sessione: SessioneParcheggio)

    // 4. Aggiornamento (quando chiudi un parcheggio attivo)
    @Query("UPDATE tabella_sessioni_parcheggio SET isAttivo = 0, endTimeStamp = :endTime WHERE id = :sessionId")
    fun chiudiParcheggio(sessionId: Long, endTime: Long)

    // Il tasto "Cancella" dello storico userà questa per nascondere il dato senza eliminarlo dalle stats
    @Query("UPDATE tabella_sessioni_parcheggio SET archiviato = 1 WHERE id = :sessionId")
    fun archiviaParcheggio(sessionId: Long)

    // 5. Eliminazione fisica dal database (la manteniamo per sicurezza)
    @Query("DELETE FROM tabella_sessioni_parcheggio WHERE id = :sessionId")
    fun eliminaParcheggio(sessionId: Long)
}