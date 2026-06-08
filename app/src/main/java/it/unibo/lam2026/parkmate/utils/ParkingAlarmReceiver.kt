package it.unibo.lam2026.parkmate.utils

import android.annotation.SuppressLint
import android.app.AlarmManager
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

        // Configurazione dell'Intent per l'apertura dell'applicazione al tap sulla notifica
        val apriAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            apriAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Costruzione e formattazione visiva della notifica
        val builder = NotificationCompat.Builder(context, channelId)
            // Punto di estensione: personalizzazione dell'icona vettoriale
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("🚗 ParkMate: Scadenza $veicolo")
            .setContentText(messaggio)
            // Abilitazione del BigTextStyle per supportare testi multilinea nel notification drawer
            .setStyle(NotificationCompat.BigTextStyle().bigText(messaggio))
            .setColor(android.graphics.Color.BLUE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            // Rimozione automatica dal drawer dopo l'interazione
            .setAutoCancel(true)
            // Binding del PendingIntent per la navigazione
            .setContentIntent(pendingIntent)

        val notificationId = System.currentTimeMillis().toInt()
        notificationManager.notify(notificationId, builder.build())
    }

    // Metodo di utility per la cancellazione degli allarmi pendenti legati a soste concluse anticipatamente
    companion object {
        fun cancellaAllarme(context: Context, requestCode: Int) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            val intent = Intent(context, ParkingAlarmReceiver::class.java).apply {
                // L'azione deve corrispondere esattamente a quella utilizzata in fase di schedulazione
                action = "SCADENZA_$requestCode"
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            alarmManager.cancel(pendingIntent)
        }
    }
}