package it.unibo.lam2026.parkmate.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import it.unibo.lam2026.parkmate.R
import it.unibo.lam2026.parkmate.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // [NUOVO] 1. Creiamo l'oggetto che gestisce la risposta dell'utente al popup delle notifiche
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Ottimo, l'utente ha detto SI. Gli allarmi di scadenza suoneranno!
        } else {
            // L'utente ha detto NO. L'app non crasherà, semplicemente non vedrà l'avviso.
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

        // [NUOVO] 2. Al primo avvio dell'app, controlliamo se serve chiedere il permesso
        chiediPermessoNotifiche()
    }

    // [NUOVO] 3. La funzione che fa apparire il popup (solo se hai Android 13 o superiore)
    private fun chiediPermessoNotifiche() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // Se non abbiamo ancora il permesso, spariamo il popup a schermo!
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}