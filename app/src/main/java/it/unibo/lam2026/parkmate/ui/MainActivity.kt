package it.unibo.lam2026.parkmate.ui

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.DetectedActivity
import it.unibo.lam2026.parkmate.R
import it.unibo.lam2026.parkmate.databinding.ActivityMainBinding
import it.unibo.lam2026.parkmate.utils.ActivityTransitionReceiver

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // NavController reso globale per consentire il routing programmatico da Intent esterni
    private lateinit var navController: androidx.navigation.NavController

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.d("ParkMate", "Permesso notifiche accordato")
        }
        // Catena dei permessi: dopo le notifiche, procede con la richiesta per l'Activity Recognition
        chiediPermessoActivityRecognition()
    }

    // Gestione della risposta al permesso di rilevamento dell'attività fisica
    private val requestActivityLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            setupActivityTransitions()
        } else {
            Toast.makeText(this, "Senza questo permesso l'app non rileverà la sosta automatica", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        binding.bottomNavigation.setupWithNavController(navController)

        // Associa un trigger di testing (Easter Egg) per simulare l'evento di discesa auto
        val tabMappa = binding.bottomNavigation.findViewById<android.view.View>(binding.bottomNavigation.menu.getItem(0).itemId)

        tabMappa.setOnLongClickListener {
            Log.d("ParkMate_Debug", "Trigger segreto attivato! Simulo il sensore di sosta.")

            val sharedPrefs = getSharedPreferences("ParkMatePrefs", android.content.Context.MODE_PRIVATE)
            sharedPrefs.edit()
                .putLong("KEY_DISCESA_AUTO_TIMESTAMP", System.currentTimeMillis() - (1 * 60 * 1000))
                .apply()

            val fintoIntentSensore = Intent(this, ActivityTransitionReceiver::class.java)
            sendBroadcast(fintoIntentSensore)

            true
        }

        // Inizializza il flusso dei permessi e verifica se l'app è stata aperta tramite una notifica
        chiediPermessoNotifiche()
        gestisciIntentNotifica(intent)
    }

    // Gestisce il reindirizzamento forzato alla mappa in seguito all'interazione con la notifica di sosta
    private fun gestisciIntentNotifica(intent: Intent?) {
        if (intent != null && intent.getBooleanExtra("APRI_SCHERMO_PARCHEGGIO", false)) {
            Log.d("ParkMate", "Notifica cliccata! Reindirizzo sulla mappa.")

            try {
                navController.navigate(R.id.mapFragment)
            } catch (e: Exception) {
                Log.e("ParkMate", "Errore navigazione: ${e.message}")
            }

            // Rimuove l'extra per evitare cicli di navigazione errati in caso di ricreazione dell'Activity (es. rotazione)
            intent.removeExtra("APRI_SCHERMO_PARCHEGGIO")
        }
    }

    private fun chiediPermessoNotifiche() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                chiediPermessoActivityRecognition()
            }
        } else {
            chiediPermessoActivityRecognition()
        }
    }

    private fun chiediPermessoActivityRecognition() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
                requestActivityLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
            } else {
                setupActivityTransitions()
            }
        } else {
            // Retrocompatibilità: su versioni precedenti ad Android 10 (Q) il permesso viene concesso tramite Manifest
            setupActivityTransitions()
        }
    }

    // Registrazione del monitoraggio transizioni tramite le API di Google Play Services
    private fun setupActivityTransitions() {
        // Rilevamento uscita dal veicolo
        val transizioneUscitaAuto = ActivityTransition.Builder()
            .setActivityType(DetectedActivity.IN_VEHICLE)
            .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT)
            .build()

        // Rilevamento stato di fermo prolungato
        val transizioneFermo = ActivityTransition.Builder()
            .setActivityType(DetectedActivity.STILL)
            .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
            .build()

        val request = ActivityTransitionRequest(listOf(transizioneUscitaAuto, transizioneFermo))

        val intent = Intent(this, ActivityTransitionReceiver::class.java)

        // FLAG_MUTABLE è obbligatorio per permettere al sistema di popolare l'Intent con gli extra del sensore
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            1001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        try {
            ActivityRecognition.getClient(this)
                .requestActivityTransitionUpdates(request, pendingIntent)
                .addOnSuccessListener {
                    Log.d("ParkMate_AR", "Riconoscimento attività avviato con successo!")
                }
                .addOnFailureListener { e ->
                    Log.e("ParkMate_AR", "Errore nell'avvio del riconoscimento: ${e.message}")
                }
        } catch (e: SecurityException) {
            Log.e("ParkMate_AR", "Errore di sicurezza: mancano i permessi", e)
        }
    }
}