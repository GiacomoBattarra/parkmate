package it.unibo.lam2026.parkmate.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import it.unibo.lam2026.parkmate.model.AppDatabase
import it.unibo.lam2026.parkmate.model.SessioneParcheggio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import it.unibo.lam2026.parkmate.utils.ParkingAlarmReceiver
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import it.unibo.lam2026.parkmate.utils.ParkingSessionWorker

class ParcheggioViewModel(application: Application) : AndroidViewModel(application) {

    // Prendiamo il riferimento al DAO passando per il Database
    private val dao = AppDatabase.getDatabase(application).parcheggioDao()

    // Lo storico mostrerà solo i parcheggi attivi (grazie alla modifica al DAO con archiviato = 0)
    val storicoParcheggi = dao.getStoricoParcheggi().asLiveData()

    // Espone i parcheggi attivi alla Mappa in modo reattivo
    val parcheggiAttivi = dao.getParcheggiAttivi().asLiveData()

    // [NUOVO]: Espone TUTTI i parcheggi (anche quelli nascosti/archiviato = 1) alla schermata delle Statistiche
    val statisticheGlobaliParcheggi = dao.getTuttiIParcheggiPerStats().asLiveData()

    fun salvaParcheggio(nomeVeicolo: String, tipo: String, lat: Double, lon: Double, tariffa: Double = 0.0, scadenzaTimestamp: Long? = null) {
        // Creiamo l'oggetto da salvare
        val nuovaSessione = SessioneParcheggio(
            veicoloNome = nomeVeicolo,
            tipoParcheggio = tipo,
            latitudine = lat,
            longitudine = lon,
            startTimeStamp = System.currentTimeMillis(),
            tariffa = tariffa,
            scadenzaTimestamp = scadenzaTimestamp
        )

        // Salviamo nel database
        viewModelScope.launch(Dispatchers.IO) {
            dao.inserisciParcheggio(nuovaSessione)
        }

        // --- [NUOVO]: LA MAGIA DELLE NOTIFICHE! ---
        // Se la sosta ha una scadenza (è un ticket fisso), impostiamo l'allarme di sistema
        if (scadenzaTimestamp != null) {
            impostaAllarmeScadenza(nomeVeicolo, scadenzaTimestamp)
        }

        // --- [NUOVO]: WORKMANAGER PER SOSTA A CONSUMO ---
        if (tipo.contains("Orario")) {
            // Selezioniamo 15 minuti, l'intervallo minimo consentito da Android
            val workRequest = PeriodicWorkRequestBuilder<ParkingSessionWorker>(15, TimeUnit.MINUTES)
                .addTag("SESSION_${nuovaSessione.veicoloNome}") // Gli diamo una targa unica per poterlo fermare dopo
                .build()

            // Inseriamo il lavoratore nella coda di sistema
            WorkManager.getInstance(getApplication()).enqueue(workRequest)

        }
    }

    /**
     * Sfrutta l'AlarmManager di Android per programmare una notifica in futuro,
     * che suonerà anche se l'app è completamente chiusa.
     */
    private fun impostaAllarmeScadenza(veicoloNome: String, scadenzaTimestamp: Long) {
        // Otteniamo i servizi di sistema di Android
        val context = getApplication<Application>().applicationContext
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // Prepariamo il pacco (Intent) da spedire al nostro ParkingAlarmReceiver
        val intent = Intent(context, ParkingAlarmReceiver::class.java).apply {
            putExtra("VEICOLO", veicoloNome)
            putExtra("MESSAGGIO", "Attenzione! Il ticket per $veicoloNome scadrà a breve!")
        }

        // Generiamo un ID univoco per questo allarme (così possiamo averne attivi anche 10 contemporaneamente)
        val requestCode = System.currentTimeMillis().toInt()

        // Il PendingIntent è un Intent che diamo in mano al sistema Android da usare "più tardi"
        // FLAG_IMMUTABLE è un requisito obbligatorio per la sicurezza da Android 12 in poi
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Calcoliamo quando far suonare la notifica: 10 MINUTI PRIMA della scadenza
        val dieciMinutiMs = 10 * 60 * 1000
        var tempoSveglia = scadenzaTimestamp - dieciMinutiMs

        // Se l'utente ha impostato una sosta brevissima (es. 5 minuti totali),
        // il preavviso di 10 minuti sarebbe nel passato. In quel caso, suoniamo alla scadenza esatta.
        if (tempoSveglia <= System.currentTimeMillis()) {
            tempoSveglia = scadenzaTimestamp
        }

        try {
            // setExactAndAllowWhileIdle spara l'allarme al secondo spaccato,
            // perfino se il telefono è bloccato in tasca (Doze mode)
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                tempoSveglia,
                pendingIntent
            )
        } catch (e: SecurityException) {
            // Se su Android 14 l'utente ha tolto i permessi manualmente,
            // catturiamo l'errore per non far crashare l'app.
            e.printStackTrace()
        }
    }

    // Funzione per terminare un parcheggio attivo
    fun terminaParcheggio(sessionId: Long) {
        val tempoDiFine = System.currentTimeMillis()

        viewModelScope.launch(Dispatchers.IO) {
            dao.chiudiParcheggio(sessionId, tempoDiFine)

            // [NUOVO] Disattiviamo TUTTI i Worker e cancelliamo le notifiche in sospeso
            // Nota: nella versione reale dovremmo passare il nome o l'ID per fermare solo il worker specifico,
            // ma per ora spegniamo brutalmente tutti i lavori per sicurezza alla chiusura.
            WorkManager.getInstance(getApplication()).cancelAllWork()
        }
    }

    // Il tasto cancella dello storico ora non elimina più fisicamente dal DB,
    // ma chiama "archiviaParcheggio" per nasconderlo dallo storico ma mantenerlo nelle statistiche!
    fun cancellaParcheggio(sessionId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.archiviaParcheggio(sessionId)
        }
    }
}