package it.unibo.lam2026.parkmate.model

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ParcheggioDao {

    // Dati usati per il rendering dei marker sulla mappa
    @Query("SELECT * FROM tabella_sessioni_parcheggio WHERE isAttivo = 1")
    fun getParcheggiAttivi(): Flow<List<SessioneParcheggio>>

    // Recupera solo i parcheggi visibili per la schermata storico
    @Query("SELECT * FROM tabella_sessioni_parcheggio WHERE archiviato = 0 ORDER BY isAttivo DESC, startTimeStamp DESC")
    fun getStoricoParcheggi(): Flow<List<SessioneParcheggio>>

    // Include anche i dati archiviati per mantenere coerenti i calcoli globali
    @Query("SELECT * FROM tabella_sessioni_parcheggio ORDER BY startTimeStamp DESC")
    fun getTuttiIParcheggiPerStats(): Flow<List<SessioneParcheggio>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun inserisciParcheggio(sessione: SessioneParcheggio)

    // Termina la sessione aggiornando i dati finali per la fatturazione/storico
    @Query("UPDATE tabella_sessioni_parcheggio SET isAttivo = 0, endTimeStamp = :endTime, costoTotale = :costo WHERE id = :sessionId")
    fun chiudiParcheggio(sessionId: Long, endTime: Long, costo: Double)

    // Nasconde il dato dall'interfaccia utente senza alterare le statistiche generali
    @Query("UPDATE tabella_sessioni_parcheggio SET archiviato = 1 WHERE id = :sessionId")
    fun archiviaParcheggio(sessionId: Long)

    @Query("DELETE FROM tabella_sessioni_parcheggio WHERE id = :sessionId")
    fun eliminaParcheggio(sessionId: Long)

    // Usato pre-chiusura per recuperare i parametri necessari al calcolo tariffario
    @Query("SELECT * FROM tabella_sessioni_parcheggio WHERE id = :sessionId LIMIT 1")
    fun getParcheggioById(sessionId: Long): SessioneParcheggio?

    // Verifica per impedire a un singolo veicolo di avere più soste attive contemporaneamente
    @Query("SELECT * FROM tabella_sessioni_parcheggio WHERE isAttivo = 1 AND veicoloNome = :nomeVeicolo LIMIT 1")
    fun getParcheggioAttivoPerVeicolo(nomeVeicolo: String): SessioneParcheggio?

    @Query("DELETE FROM tabella_sessioni_parcheggio WHERE veicoloNome = :nomeVeicolo AND isAttivo = 1")
    fun eliminaParcheggiAttiviPerVeicolo(nomeVeicolo: String)

    // Propaga il cambio nome del veicolo mantenendo l'integrità dei report storici
    @Query("UPDATE tabella_sessioni_parcheggio SET veicoloNome = :nuovoNome WHERE veicoloNome = :vecchioNome AND isAttivo = 0")
    fun aggiornaNomeVeicoloNelloStorico(vecchioNome: String, nuovoNome: String)
}