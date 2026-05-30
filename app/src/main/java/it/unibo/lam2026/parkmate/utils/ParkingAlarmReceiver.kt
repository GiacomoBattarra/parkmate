package it.unibo.lam2026.parkmate.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import it.unibo.lam2026.parkmate.R
import it.unibo.lam2026.parkmate.ui.MainActivity

class ParkingAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val veicolo = intent.getStringExtra("VEICOLO") ?: "Veicolo"
        val messaggio = intent.getStringExtra("MESSAGGIO") ?: "Il tuo ticket scade a breve!"

        mostraNotifica(context, veicolo, messaggio)
    }

    private fun mostraNotifica(context: Context, veicolo: String, messaggio: String) {
        val channelId = "parkmate_channel"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Avvisi Scadenza Parcheggio",
                NotificationManager.IMPORTANCE_HIGH
            )
            channel.description = "Notifiche per le scadenze dei ticket fissi"
            notificationManager.createNotificationChannel(channel)
        }

        // 1. [NUOVO] Intento per APRIRE L'APP quando l'utente tocca la notifica!
        val apriAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            apriAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 2. [NUOVO] Design elegante della notifica
        val builder = NotificationCompat.Builder(context, channelId)
            // Usiamo un'iconina standard di Android legata al tempo/eventi (o mettine una tua personalizzata!)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("🚗 ParkMate: Scadenza $veicolo")
            .setContentText(messaggio)
            // Stile Espanso: se il testo è lungo, Android permette all'utente di espandere la tendina
            .setStyle(NotificationCompat.BigTextStyle().bigText(messaggio))
            // Tinge l'icona del colore principale della tua app (blu/viola di default)
            .setColor(android.graphics.Color.BLUE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true) // La notifica sparisce quando la tocchi
            .setContentIntent(pendingIntent) // Aggancia l'apertura dell'app!

        val notificationId = System.currentTimeMillis().toInt()
        notificationManager.notify(notificationId, builder.build())
    }
}