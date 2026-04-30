package it.unibo.lam2026.parkmate.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import it.unibo.lam2026.parkmate.R
import it.unibo.lam2026.parkmate.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Inizializziamo il binding (che ora vede solo il nav_host e la bottom bar)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 2. Troviamo il "motore" di navigazione
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        // 3. Colleghiamo la barra in basso al motore
        binding.bottomNavigation.setupWithNavController(navController)
    }
}