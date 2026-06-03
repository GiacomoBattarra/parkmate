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

        // [NUOVO] Calcoliamo il Parking Effort Score prima di creare la sessione
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
            parkingEffortScore = scoreCalcolato // [NUOVO] Passiamo il punteggio alla colonna del DB!
        )

        // GESTIONE DOPPIONI E SALVATAGGIO IN BACKGROUND
        viewModelScope.launch(Dispatchers.IO) {

            // A. Cerchiamo se la macchina è già parcheggiata altrove
            val vecchiaSessione = dao.getParcheggioAttivoPerVeicolo(nomeVeicolo)

            // B. Se sì, la chiudiamo in automatico per tutti i casi (Libero, Fisso, Orario)
            if (vecchiaSessione != null) {
                var costoCalcolato = 0.0

                // Calcolo solo se c'è una tariffa (Fissa o Oraria)
                if (vecchiaSessione.tariffa > 0.0) {
                    if (vecchiaSessione.tipoParcheggio.contains("Fiss", ignoreCase = true)) {
                        costoCalcolato = vecchiaSessione.tariffa
                    } else {
                        val millisecondiTrascorsi = tempoAttuale - vecchiaSessione.startTimeStamp

                        // 1. Troviamo i minuti esatti (con i decimali)
                        val minutiTrascorsi = millisecondiTrascorsi.toDouble() / (1000.0 * 60.0)

                        // 2. Calcoliamo quanto costa 1 singolo minuto
                        val costoAlMinuto = vecchiaSessione.tariffa / 60.0

                        // 3. Moltiplichiamo i minuti per il costo al minuto
                        costoCalcolato = minutiTrascorsi * costoAlMinuto

                        // Arrotondiamo ai classici 2 decimali (es. 1.45€)
                        costoCalcolato = Math.round(costoCalcolato * 100.0) / 100.0
                    }
                }

                // Chiudiamo il vecchio parcheggio nel database
                dao.chiudiParcheggio(vecchiaSessione.id, tempoAttuale, costoCalcolato)

                // --- NUOVO: MOSTRA L'AVVISO ALL'UTENTE ---
                // Dobbiamo spostarci sul Thread Principale (Main) per mostrare roba grafica
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        getApplication(),
                        "Sosta precedente di '$nomeVeicolo' terminata in automatico.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            // C. Salviamo la nuova sosta
            dao.inserisciParcheggio(nuovaSessione)
        }

        // Impostiamo l'allarme se c'è una scadenza
        if (scadenzaTimestamp != null) {
            impostaAllarmeScadenza(nomeVeicolo, scadenzaTimestamp)
        }

        // Avviamo il worker se è a pagamento orario
        if (tipo.contains("Orario")) {
            val workRequest = PeriodicWorkRequestBuilder<ParkingSessionWorker>(15, TimeUnit.MINUTES)
                .addTag("SESSION_${nuovaSessione.veicoloNome}")
                .build()

            WorkManager.getInstance(getApplication()).enqueue(workRequest)
        }
    }

    // [NUOVO] Funzione di supporto per l'algoritmo del Parking Effort Score
    private fun calcolaAndResetParkingEffortScore(tempoAttuale: Long): Int? {
        // Accediamo alle SharedPreferences in modo sicuro tramite il contesto dell'applicazione
        val sharedPrefs = getApplication<Application>().getSharedPreferences("ParkMatePrefs", Context.MODE_PRIVATE)

        // Leggiamo il timestamp della discesa. Se non c'è, restituisce 0L (significa parcheggio manuale)
        val timestampDiscesa = sharedPrefs.getLong("KEY_DISCESA_AUTO_TIMESTAMP", 0L)

        if (timestampDiscesa == 0L) {
            // L'utente ha avviato il parcheggio a mano senza passare dall'Activity Recognition
            return null
        }

        // Calcoliamo la differenza in millisecondi e la convertiamo in minuti
        val deltaMs = tempoAttuale - timestampDiscesa
        val minutiTrascorsi = deltaMs / (1000 * 60)

        // Applichiamo la scala di valutazione concordata (da 1 a 5)
        val score = when {
            minutiTrascorsi < 2 -> 1   // Sosta quasi immediata (Faticosità: Minima)
            minutiTrascorsi < 5 -> 2   // Qualche minuto di ricerca (Faticosità: Bassa)
            minutiTrascorsi < 10 -> 3  // Sforzo normale urbano (Faticosità: Media)
            minutiTrascorsi < 20 -> 4  // Ha girato molto o parcheggiato lontano (Faticosità: Alta)
            else -> 5                  // Oltre 20 minuti a piedi/ricerca (Faticosità: Critica)
        }

        // CRUCIALE: Puliamo subito la preferenza!
        // Altrimenti il prossimo parcheggio manuale riutilizzerebbe questo vecchio dato.
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
                        // LA NOSTRA NUOVA MATEMATICA AL MINUTO
                        val millisecondiTrascorsi = tempoDiFine - sessione.startTimeStamp
                        val minutiTrascorsi = millisecondiTrascorsi.toDouble() / (1000.0 * 60.0)
                        val costoAlMinuto = sessione.tariffa / 60.0

                        costoCalcolato = minutiTrascorsi * costoAlMinuto

                        // Arrotondamento ai centesimi
                        costoCalcolato = Math.round(costoCalcolato * 100.0) / 100.0
                    }
                }
                dao.chiudiParcheggio(sessionId, tempoDiFine, costoCalcolato)
            }

            WorkManager.getInstance(getApplication()).cancelAllWork()
        }
    }

    fun cancellaParcheggio(sessionId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.archiviaParcheggio(sessionId)
        }
    }
}