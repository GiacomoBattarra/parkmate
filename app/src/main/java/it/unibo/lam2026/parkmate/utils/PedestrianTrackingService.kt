package it.unibo.lam2026.parkmate.utils

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import it.unibo.lam2026.parkmate.R
import it.unibo.lam2026.parkmate.model.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

class PedestrianTrackingService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    private var ultimaPosizione: Location? = null
    private var distanzaTotaleMetri: Float = 0f
    private var timestampInizioCamminata: Long = 0

    // Flag di stato per impedire l'azzeramento accidentale delle metriche in caso di riavvii concorrenti del servizio
    private var isTracking = false

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP_TRACKING") {
            fermaTracciamento()
            return START_NOT_STICKY
        }

        // Ignora i tentativi di avvio sovrapposti per mantenere la continuità del tracciamento corrente
        if (!isTracking) {
            isTracking = true
            timestampInizioCamminata = System.currentTimeMillis()
            distanzaTotaleMetri = 0f

            avviaServizioInForeground()
            iniziaLetturaGPS()
        }

        return START_STICKY
    }

    private fun avviaServizioInForeground() {
        val channelId = "pedestrian_tracking_channel"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Tracciamento Camminata",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }

        // Configurazione dell'Intent per consentire l'interruzione manuale del servizio direttamente dalla notifica
        val stopIntent = Intent(this, PedestrianTrackingService::class.java).apply {
            action = "STOP_TRACKING"
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notifica: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("🚶 ParkMate: Camminata in corso")
            .setContentText("Stiamo calcolando lo sforzo...")
            .setSmallIcon(android.R.drawable.ic_menu_directions)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "SONO ARRIVATO!", stopPendingIntent)
            .setOngoing(true)
            .build()

        startForeground(3001, notifica)
    }

    private fun iniziaLetturaGPS() {
        // Configurazione dei parametri di campionamento spaziale e temporale
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000)
            .setMinUpdateDistanceMeters(2f)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    if (ultimaPosizione != null) {
                        // Calcolo incrementale della distanza lineare tra le coordinate rilevate
                        val distanzaParziale = ultimaPosizione!!.distanceTo(location)
                        distanzaTotaleMetri += distanzaParziale
                        Log.d("ParkMate_Effort", "Percorsi: $distanzaTotaleMetri metri")
                    }
                    ultimaPosizione = location
                }
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            Log.e("ParkMate_Effort", "Permessi GPS mancanti")
        }
    }

    private fun fermaTracciamento() {
        // Verifica di sicurezza: assicura che il callback sia stato inizializzato prima di tentarne la deregistrazione
        if (!::locationCallback.isInitialized) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        isTracking = false

        // Deregistrazione del listener GPS per ottimizzare il consumo energetico
        fusedLocationClient.removeLocationUpdates(locationCallback)

        val durataSecondi = (System.currentTimeMillis() - timestampInizioCamminata) / 1000

        val dao = AppDatabase.getDatabase(applicationContext).parcheggioDao()

        // Operazioni di I/O sul database eseguite in background per preservare la fluidità del Main Thread
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val parcheggiAttivi = dao.getParcheggiAttivi().first()

                // Identificazione delle sessioni di sosta che necessitano del calcolo dello sforzo pedonale
                val parcheggiSenzaScore = parcheggiAttivi.filter { it.parkingEffortScore == null }

                if (parcheggiSenzaScore.isNotEmpty()) {
                    val calcoloScore = when {
                        durataSecondi < 120 -> 1
                        durataSecondi < 300 -> 2
                        durataSecondi < 600 -> 3
                        durataSecondi < 1200 -> 4
                        else -> 5
                    }

                    // Propagazione dell'Effort Score a tutte le sessioni contemporanee
                    for (parcheggioCorrente in parcheggiSenzaScore) {
                        val parcheggioAggiornato = parcheggioCorrente.copy(
                            distanzaPiediMetri = distanzaTotaleMetri,
                            durataPiediSecondi = durataSecondi,
                            parkingEffortScore = calcoloScore
                        )
                        dao.inserisciParcheggio(parcheggioAggiornato)
                        Log.d("ParkMate_Effort", "Assegnato a ${parcheggioCorrente.veicoloNome}! Score: $calcoloScore")
                    }
                }
            } catch (e: Exception) {
                Log.e("ParkMate_Effort", "Errore DB: ${e.message}")
            } finally {
                // Terminazione formale del servizio delegata al completamento delle routine di persistenza
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}