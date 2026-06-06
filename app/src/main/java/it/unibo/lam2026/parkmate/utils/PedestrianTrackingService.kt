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

    // NUOVO: Flag per evitare che il servizio si riavvii da zero se stiamo già tracciando
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

        // NUOVO CONTROLLO (Risolve Bug 2):
        // Se stiamo GIÀ tracciando (es. 2° parcheggio ravvicinato), ignoriamo l'avvio
        // e continuiamo a contare i passi senza azzerare il timer!
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

        // --- NUOVO: Prepariamo il segnale "STOP" per il bottone ---
        val stopIntent = Intent(this, PedestrianTrackingService::class.java).apply {
            action = "STOP_TRACKING"
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Creiamo la notifica con il bottone extra
        val notifica: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("🚶 ParkMate: Camminata in corso")
            .setContentText("Stiamo calcolando lo sforzo...")
            .setSmallIcon(android.R.drawable.ic_menu_directions)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "SONO ARRIVATO!", stopPendingIntent) // ECCO IL BOTTONE!
            .setOngoing(true)
            .build()

        startForeground(3001, notifica)
    }

    private fun iniziaLetturaGPS() {
        // Configuriamo ogni quanto vogliamo un punto GPS (Es. ogni 3 secondi, alta precisione)
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000)
            .setMinUpdateDistanceMeters(2f) // Aggiorna solo se l'utente fa almeno 2 metri (filtra i tremolii GPS)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    if (ultimaPosizione != null) {
                        // CALCOLO DISTANZA: La magia matematica nativa di Android
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
        // 1. CONTROLLO DI SICUREZZA ANTI-CRASH
        // Se non abbiamo mai inizializzato la lettura GPS, significa che il tracking non era mai partito.
        if (!::locationCallback.isInitialized) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return // Usciamo subito senza provare a salvare nel database!
        }

        isTracking = false // Spegniamo il motore
        // 2. Fermiamo il GPS per non consumare batteria
        fusedLocationClient.removeLocationUpdates(locationCallback)

        // 3. Calcoliamo i secondi effettivi
        val durataSecondi = (System.currentTimeMillis() - timestampInizioCamminata) / 1000

        val dao = AppDatabase.getDatabase(applicationContext).parcheggioDao()

        // Apriamo il thread di background per salvare
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Usiamo .first() preso in prestito da kotlinx.coroutines.flow.first
                val parcheggiAttivi = dao.getParcheggiAttivi().first()

                // --- NUOVA LOGICA (Risolve Bug 2) ---
                // Invece di prendere solo il primo parcheggio, prendiamo TUTTI i parcheggi
                // attivi che non hanno ancora ricevuto uno score!
                val parcheggiSenzaScore = parcheggiAttivi.filter { it.parkingEffortScore == null }

                if (parcheggiSenzaScore.isNotEmpty()) {
                    val calcoloScore = when {
                        durataSecondi < 120 -> 1
                        durataSecondi < 300 -> 2
                        durataSecondi < 600 -> 3
                        durataSecondi < 1200 -> 4
                        else -> 5
                    }

                    // Assegnamo lo stesso score a TUTTI i parcheggi che hai fatto assieme!
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
                // SPEGNIAMO IL SERVIZIO SOLO DOPO AVER SALVATO I DATI!
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}