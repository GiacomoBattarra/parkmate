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

    // doWork() viene eseguito in automatico in background da Android
    override suspend fun doWork(): Result {

        // 1. Apriamo il database
        val dao = AppDatabase.getDatabase(context).parcheggioDao()

        // Leggiamo la lista attuale dei parcheggi attivi (usiamo .first() per estrarre la lista dal Flow)
        val parcheggiAttivi = dao.getParcheggiAttivi().first()

        // 2. Filtriamo SOLO i parcheggi a pagamento orario
        val sosteOravie = parcheggiAttivi.filter { it.tipoParcheggio.contains("Orario") }

        // Se non ci sono soste orarie attive, diciamo ad Android che il lavoro è finito con successo
        if (sosteOravie.isEmpty()) {
            return Result.success()
        }

        // 3. Per ogni sosta oraria attiva, calcoliamo il costo e spariamo la notifica
        val tempoAttuale = System.currentTimeMillis()

        for (sosta in sosteOravie) {
            // Calcolo del tempo trascorso in ORE (con i decimali, es. 1.5 ore)
            val millisecondiTrascorsi = tempoAttuale - sosta.startTimeStamp
            val oreTrascorse = millisecondiTrascorsi / (1000.0 * 60.0 * 60.0)

            // Calcolo del costo parziale: Ore * Tariffa Oraria
            val costoAttuale = oreTrascorse * sosta.tariffa

            // Formattiamo il costo con 2 decimali (es. "3.50 €")
            val costoFormattato = String.format("%.2f", costoAttuale)

            // Formattiamo il tempo trascorso in minuti totali per un messaggio più leggibile
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
                NotificationManager.IMPORTANCE_LOW // Low: appare silenziosamente senza far suonare in modo aggressivo
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
        // Non usiamo setAutoCancel(true) perché vogliamo che rimanga visibile come promemoria!

        notificationManager.notify(notificaId, builder.build())
    }
}