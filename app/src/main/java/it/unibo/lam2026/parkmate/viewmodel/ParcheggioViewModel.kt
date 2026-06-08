package it.unibo.lam2026.parkmate.viewmodel

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import it.unibo.lam2026.parkmate.model.AppDatabase
import it.unibo.lam2026.parkmate.model.SessioneParcheggio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    private val dao = AppDatabase.getDatabase(application).parcheggioDao()

    val storicoParcheggi = dao.getStoricoParcheggi().asLiveData()
    val parcheggiAttivi = dao.getParcheggiAttivi().asLiveData()
    val statisticheGlobaliParcheggi = dao.getTuttiIParcheggiPerStats().asLiveData()

    fun salvaParcheggio(
        nomeVeicolo: String,
        tipo: String,
        lat: Double,
        lon: Double,
        tariffa: Double = 0.0,
        scadenzaTimestamp: Long? = null,
        nota: String? = null,
        fotoPath: String? = null
    ) {
        val tempoAttuale = System.currentTimeMillis()

        // Calcolo del Parking Effort Score per la telemetria della sosta
        val scoreCalcolato = calcolaAndResetParkingEffortScore(tempoAttuale)

        val nuovaSessione = SessioneParcheggio(
            veicoloNome = nomeVeicolo,
            tipoParcheggio = tipo,
            latitudine = lat,
            longitudine = lon,
            startTimeStamp = tempoAttuale,
            tariffa = tariffa,
            scadenzaTimestamp = scadenzaTimestamp,
            nota = nota,
            fotoPath = fotoPath,
            parkingEffortScore = scoreCalcolato
        )

        // Deregistrazione di eventuali job pendenti per evitare sovrapposizioni di notifiche
        WorkManager.getInstance(getApplication()).cancelAllWorkByTag("SESSION_$nomeVeicolo")

        // Esecuzione asincrona per la gestione dei conflitti e persistenza della sessione
        viewModelScope.launch(Dispatchers.IO) {

            // Risoluzione dei conflitti: individuazione di eventuali sessioni attive per il medesimo veicolo
            val vecchiaSessione = dao.getParcheggioAttivoPerVeicolo(nomeVeicolo)

            // Terminazione automatica della sessione pregressa e calcolo degli oneri maturati
            if (vecchiaSessione != null) {
                var costoCalcolato = 0.0

                if (vecchiaSessione.tariffa > 0.0) {
                    if (vecchiaSessione.tipoParcheggio.contains("Fiss", ignoreCase = true)) {
                        costoCalcolato = vecchiaSessione.tariffa
                    } else {
                        // Calcolo proporzionale della tariffa basato sui minuti effettivi di sosta
                        val millisecondiTrascorsi = tempoAttuale - vecchiaSessione.startTimeStamp
                        val minutiTrascorsi = millisecondiTrascorsi.toDouble() / (1000.0 * 60.0)
                        val costoAlMinuto = vecchiaSessione.tariffa / 60.0

                        costoCalcolato = minutiTrascorsi * costoAlMinuto

                        // Arrotondamento ai centesimi di Euro
                        costoCalcolato = Math.round(costoCalcolato * 100.0) / 100.0
                    }
                }

                dao.chiudiParcheggio(vecchiaSessione.id, tempoAttuale, costoCalcolato)

                // Feedback UI per notificare all'utente la risoluzione del conflitto
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        getApplication(),
                        "Sosta precedente di '$nomeVeicolo' terminata in automatico.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            // Persistenza della nuova sessione
            dao.inserisciParcheggio(nuovaSessione)
        }

        if (scadenzaTimestamp != null) {
            impostaAllarmeScadenza(nomeVeicolo, scadenzaTimestamp)
        }

        // Schedulazione del job periodico per l'aggiornamento della spesa nelle soste orarie
        if (tipo.contains("Orario")) {
            val workRequest = PeriodicWorkRequestBuilder<ParkingSessionWorker>(1, TimeUnit.HOURS)
                .addTag("SESSION_${nuovaSessione.veicoloNome}")
                .build()

            WorkManager.getInstance(getApplication()).enqueue(workRequest)
        }
    }

    // Valutazione dello sforzo di parcheggio basata sull'integrazione con Activity Recognition
    private fun calcolaAndResetParkingEffortScore(tempoAttuale: Long): Int? {
        val sharedPrefs = getApplication<Application>().getSharedPreferences("ParkMatePrefs", Context.MODE_PRIVATE)

        // Recupero del timestamp generato al rilevamento dell'uscita dal veicolo
        val timestampDiscesa = sharedPrefs.getLong("KEY_DISCESA_AUTO_TIMESTAMP", 0L)

        // Fallback: se assente, la sosta è stata inizializzata manualmente
        if (timestampDiscesa == 0L) {
            return null
        }

        val deltaMs = tempoAttuale - timestampDiscesa
        val minutiTrascorsi = deltaMs / (1000 * 60)

        // Classificazione del Parking Effort Score (Scala 1-5)
        val score = when {
            minutiTrascorsi < 2 -> 1
            minutiTrascorsi < 5 -> 2
            minutiTrascorsi < 10 -> 3
            minutiTrascorsi < 20 -> 4
            else -> 5
        }

        // Reset del flag per prevenire l'inquinamento dei dati in future sessioni manuali
        sharedPrefs.edit().remove("KEY_DISCESA_AUTO_TIMESTAMP").apply()

        return score
    }

    private fun impostaAllarmeScadenza(veicoloNome: String, scadenzaTimestamp: Long) {
        val context = getApplication<Application>().applicationContext
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, ParkingAlarmReceiver::class.java).apply {
            putExtra("VEICOLO", veicoloNome)
            putExtra("MESSAGGIO", "Attenzione! Il ticket per $veicoloNome scadrà a breve!")
        }

        val requestCode = System.currentTimeMillis().toInt()

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Schedulazione dell'allarme con anticipo di 10 minuti rispetto alla scadenza reale
        val dieciMinutiMs = 10 * 60 * 1000
        var tempoSveglia = scadenzaTimestamp - dieciMinutiMs

        if (tempoSveglia <= System.currentTimeMillis()) {
            tempoSveglia = scadenzaTimestamp
        }

        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                tempoSveglia,
                pendingIntent
            )
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    fun terminaParcheggio(sessionId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val tempoDiFine = System.currentTimeMillis()
            val sessione = dao.getParcheggioById(sessionId)

            if (sessione != null) {
                var costoCalcolato = 0.0

                if (sessione.tariffa > 0.0) {
                    if (sessione.tipoParcheggio.contains("Fiss", ignoreCase = true)) {
                        costoCalcolato = sessione.tariffa
                    } else {
                        val millisecondiTrascorsi = tempoDiFine - sessione.startTimeStamp
                        val minutiTrascorsi = millisecondiTrascorsi.toDouble() / (1000.0 * 60.0)
                        val costoAlMinuto = sessione.tariffa / 60.0

                        costoCalcolato = minutiTrascorsi * costoAlMinuto
                        costoCalcolato = Math.round(costoCalcolato * 100.0) / 100.0
                    }
                }

                dao.chiudiParcheggio(sessionId, tempoDiFine, costoCalcolato)
                WorkManager.getInstance(getApplication()).cancelAllWorkByTag("SESSION_${sessione.veicoloNome}")
            }
        }
    }

    fun cancellaParcheggio(sessionId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.archiviaParcheggio(sessionId)
        }
    }

    // Gestione della referenzialità dei dati storici a seguito dell'eliminazione logica di un veicolo
    fun gestisciEliminazioneVeicolo(nomeVeicolo: String) {
        viewModelScope.launch(Dispatchers.IO) {

            val sostaInCorso = dao.getParcheggioAttivoPerVeicolo(nomeVeicolo)

            // Chiusura forzata della sessione attiva per garantirne la storicizzazione prima della rimozione del veicolo
            if (sostaInCorso != null) {
                val tempoDiFine = System.currentTimeMillis()
                var costoCalcolato = 0.0

                if (sostaInCorso.tariffa > 0.0) {
                    if (sostaInCorso.tipoParcheggio.contains("Fiss", ignoreCase = true)) {
                        costoCalcolato = sostaInCorso.tariffa
                    } else {
                        val minutiTrascorsi = (tempoDiFine - sostaInCorso.startTimeStamp).toDouble() / (1000.0 * 60.0)
                        costoCalcolato = minutiTrascorsi * (sostaInCorso.tariffa / 60.0)
                        costoCalcolato = Math.round(costoCalcolato * 100.0) / 100.0
                    }
                }

                dao.chiudiParcheggio(sostaInCorso.id, tempoDiFine, costoCalcolato)
            }

            // Etichettatura batch dei record storici associati per preservare l'integrità visiva dello storico
            val nuovoNome = "$nomeVeicolo [ELIMINATO]"
            dao.aggiornaNomeVeicoloNelloStorico(vecchioNome = nomeVeicolo, nuovoNome = nuovoNome)
        }
    }
}