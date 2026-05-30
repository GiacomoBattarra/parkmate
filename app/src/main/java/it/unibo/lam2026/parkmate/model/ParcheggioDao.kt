package it.unibo.lam2026.parkmate.model

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ParcheggioDao {

    // 1. Lettura Parcheggi Attivi (per la Mappa)
    @Query("SELECT * FROM tabella_sessioni_parcheggio WHERE isAttivo = 1")
    fun getParcheggiAttivi(): Flow<List<SessioneParcheggio>>

    // 2. Lettura Storico (FUSIONE): Prende i parcheggi CHIUSI (isAttivo=0) E NON ARCHIVIATI (archiviato=0)
    @Query("SELECT * FROM tabella_sessioni_parcheggio WHERE isAttivo = 0 AND archiviato = 0 ORDER BY startTimeStamp DESC")
    fun getStoricoParcheggi(): Flow<List<SessioneParcheggio>>

    // 3. Statistiche (IL TUO CODICE): legge tutto il database globale
    @Query("SELECT * FROM tabella_sessioni_parcheggio ORDER BY startTimeStamp DESC")
    fun getTuttiIParcheggiPerStats(): Flow<List<SessioneParcheggio>>

    // 4. Scrittura
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun inserisciParcheggio(sessione: SessioneParcheggio)

    // 5. Aggiornamento (CODICE DEL TUO AMICO): Chiude il parcheggio e salva il COSTO
    @Query("UPDATE tabella_sessioni_parcheggio SET isAttivo = 0, endTimeStamp = :endTime, costoTotale = :costo WHERE id = :sessionId")
    fun chiudiParcheggio(sessionId: Long, endTime: Long, costo: Double)

    // 6. Archiviazione (IL TUO CODICE): Nasconde il dato dallo storico senza eliminarlo
    @Query("UPDATE tabella_sessioni_parcheggio SET archiviato = 1 WHERE id = :sessionId")
    fun archiviaParcheggio(sessionId: Long)

    // 7. Eliminazione fisica dal database (IL TUO CODICE)
    @Query("DELETE FROM tabella_sessioni_parcheggio WHERE id = :sessionId")
    fun eliminaParcheggio(sessionId: Long)
}