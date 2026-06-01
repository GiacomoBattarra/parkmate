package it.unibo.lam2026.parkmate.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import it.unibo.lam2026.parkmate.ui.MainActivity

class ActivityTransitionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (ActivityTransitionResult.hasResult(intent)) {
            val result = ActivityTransitionResult.extractResult(intent) ?: return

            for (event in result.transitionEvents) {
                if (event.activityType == DetectedActivity.IN_VEHICLE &&
                    event.transitionType == com.google.android.gms.location.ActivityTransition.ACTIVITY_TRANSITION_EXIT) {

                    Log.d("ParkMate_AR", "Utente sceso dall'auto! Lancio la notifica.")
                    val sharedPrefs = context.getSharedPreferences("ParkMatePrefs", Context.MODE_PRIVATE)
                    sharedPrefs.edit().putLong("KEY_DISCESA_AUTO_TIMESTAMP", System.currentTimeMillis()).apply()

                    inviaNotificaSosta(context)
                }
            }
        }
    }

    private fun inviaNotificaSosta(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "parkmate_auto_park"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Rilevamento Sosta Automatica",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        // Prepariamo l'intent per aprire la MainActivity portandoci dietro un flag
        val apriAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("APRI_SCHERMO_PARCHEGGIO", true) // Il nostro messaggio segreto!
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            2002,
            apriAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setContentTitle("🚗 Sei sceso dall'auto?")
            .setContentText("ParkMate ha rilevato che ti sei fermato. Tocca qui per salvare la posizione del parcheggio!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        notificationManager.notify(999, builder.build())
    }
}