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

    // Launcher esistente per le notifiche
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.d("ParkMate", "Permesso notifiche accordato")
        }
        // [NUOVO] Una volta gestite le notifiche, chiediamo il permesso per l'Activity Recognition
        chiediPermessoActivityRecognition()
    }

    // [NUOVO] Launcher specifico per il permesso di Activity Recognition
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
        val navController = navHostFragment.navController

        binding.bottomNavigation.setupWithNavController(navController)

        // Facciamo partire la catena dei permessi all'avvio
        chiediPermessoNotifiche()
        // [NUOVO] Controlliamo se l'app è stata aperta dalla notifica di sosta
        gestisciIntentNotifica(intent)
    }

    // [NUOVO] Intercetta il flag della notifica e mostra il BottomSheet
    private fun gestisciIntentNotifica(intent: Intent?) {
        if (intent != null && intent.getBooleanExtra("APRI_SCHERMO_PARCHEGGIO", false)) {
            Log.d("ParkMate", "Notifica cliccata! Mostro il BottomSheet di sosta.")

            // Istanziamo e mostriamo direttamente il vostro frammento grafico esistente
            val bottomSheet = ParkBottomSheetFragment()
            bottomSheet.show(supportFragmentManager, "ParkBottomSheetFragment")
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

    // [NUOVO] Funzione per verificare e richiedere il permesso di movimento
    private fun chiediPermessoActivityRecognition() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
                requestActivityLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
            } else {
                setupActivityTransitions()
            }
        } else {
            // Su versioni vecchie di Android il permesso era automatico se presente nel Manifest
            setupActivityTransitions()
        }
    }

    // [NUOVO] Configurazione e registrazione del monitoraggio dei sensori Google
    private fun setupActivityTransitions() {
        // Definiamo quale evento ci interessa: uscire (EXIT) da un veicolo (IN_VEHICLE)
        val transizioneUscitaAuto = ActivityTransition.Builder()
            .setActivityType(DetectedActivity.IN_VEHICLE)
            .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT) // <-- Cambiato qui!
            .build()

        // Creiamo la lista delle transizioni da osservare (nel nostro caso ne basta una)
        val request = ActivityTransitionRequest(listOf(transizioneUscitaAuto))

        // Prepariamo l'intent per svegliare il Receiver creato nello Step 2
        val intent = Intent(this, ActivityTransitionReceiver::class.java)

        // ATTENZIONE: FLAG_MUTABLE è obbligatorio qui!
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            1001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        // Registriamo la richiesta su Google Play Services
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