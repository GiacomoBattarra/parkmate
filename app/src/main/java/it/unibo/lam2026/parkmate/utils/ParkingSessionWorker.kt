package it.unibo.lam2026.parkmate.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import it.unibo.lam2026.parkmate.R
import it.unibo.lam2026.parkmate.model.AppDatabase
import it.unibo.lam2026.parkmate.ui.MainActivity
import kotlinx.coroutines.flow.first

class ParkingSessionWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    // Punto di ingresso del Worker per l'esecuzione asincrona in background schedulata dal sistema
    override suspend fun doWork(): Result {

        // Inizializzazione del Data Access Object per l'accesso locale
        val dao = AppDatabase.getDatabase(context).parcheggioDao()

        // Recupero dello snapshot corrente dei parcheggi attivi consumando il Flow
        val parcheggiAttivi = dao.getParcheggiAttivi().first()

        // Isolamento delle sessioni soggette a tariffazione oraria progressiva
        val sosteOravie = parcheggiAttivi.filter { it.tipoParcheggio.contains("Orario") }

        // Termina anticipatamente il task in assenza di sessioni rilevanti, risparmiando risorse
        if (sosteOravie.isEmpty()) {
            return Result.success()
        }

        // Calcolo dinamico della spesa maturata e aggiornamento dei promemoria per l'utente
        val tempoAttuale = System.currentTimeMillis()

        for (sosta in sosteOravie) {
            val millisecondiTrascorsi = tempoAttuale - sosta.startTimeStamp

            // Calcolo proporzionale dei costi con precisione al minuto
            val minutiTrascorsiDouble = millisecondiTrascorsi.toDouble() / (1000.0 * 60.0)
            val costoAlMinuto = sosta.tariffa / 60.0
            val costoAttuale = minutiTrascorsiDouble * costoAlMinuto

            // Formattazione della valuta
            val costoFormattato = String.format("%.2f", costoAttuale)

            // Conversione della durata in minuti interi per la visualizzazione nella notifica
            val minutiTrascorsi = (millisecondiTrascorsi / (1000 * 60)).toInt()

            val messaggio = "Sosta attiva da $minutiTrascorsi min.\nCosto stimato: $costoFormattato €"
            mostraNotifica(sosta.veicoloNome, messaggio, sosta.id.toInt())
        }

        return Result.success()
    }

    private fun mostraNotifica(veicolo: String, messaggio: String, notificaId: Int) {
        val channelId = "parkmate_session_channel"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Promemoria Soste Attive",
                // Livello di importanza basso: la notifica appare nel drawer senza interruzioni visive/sonore
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }

        val apriAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            apriAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentTitle("⏱️ ParkMate: $veicolo")
            .setContentText(messaggio)
            .setStyle(NotificationCompat.BigTextStyle().bigText(messaggio))
            .setColor(android.graphics.Color.BLUE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)

        // L'assenza di setAutoCancel mantiene la notifica persistente come promemoria continuo

        notificationManager.notify(notificaId, builder.build())
    }
}