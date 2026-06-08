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
            // Elaborazione degli eventi generati dalle API di Google Play Services (Activity Recognition)
            val result = ActivityTransitionResult.extractResult(intent) ?: return

            for (event in result.transitionEvents) {

                // Rilevamento uscita dal veicolo: memorizzazione del timestamp per il tracciamento della sosta
                if (event.activityType == DetectedActivity.IN_VEHICLE &&
                    event.transitionType == com.google.android.gms.location.ActivityTransition.ACTIVITY_TRANSITION_EXIT) {

                    Log.d("ParkMate_AR", "Sensore Reale: Utente sceso dall'auto! Salvo timestamp.")

                    val sharedPrefs = context.getSharedPreferences("ParkMatePrefs", Context.MODE_PRIVATE)
                    sharedPrefs.edit().putLong("KEY_DISCESA_AUTO_TIMESTAMP", System.currentTimeMillis()).apply()

                    inviaNotificaSosta(context)
                }

                // Rilevamento stato di fermo (raggiungimento destinazione): interruzione del tracking pedonale
                if (event.activityType == DetectedActivity.STILL &&
                    event.transitionType == com.google.android.gms.location.ActivityTransition.ACTIVITY_TRANSITION_ENTER) {

                    Log.d("ParkMate_AR", "Arrivo rilevato (STILL). Fermo il tracciamento della camminata.")

                    // Dispatch dell'intent di terminazione al servizio in background dedicato
                    val stopIntent = Intent(context, it.unibo.lam2026.parkmate.utils.PedestrianTrackingService::class.java).apply {
                        action = "STOP_TRACKING"
                    }
                    context.startService(stopIntent)
                }
            }
        } else {
            // Gestione del flusso di testing (Mock): attivazione manuale del broadcast bypassando il sensore
            Log.d("ParkMate_AR", "Trigger Debug: Forzo l'apparizione della notifica.")

            // Il timestamp simulato viene preconfigurato dall'interfaccia chiamante, si procede con il push della notifica
            inviaNotificaSosta(context)
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

        // Configurazione dell'Intent per il routing automatico verso il frammento mappa all'apertura dell'applicazione
        val apriAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("APRI_SCHERMO_PARCHEGGIO", true)
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