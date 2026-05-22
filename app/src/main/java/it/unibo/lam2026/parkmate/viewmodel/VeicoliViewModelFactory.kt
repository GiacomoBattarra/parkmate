package it.unibo.lam2026.parkmate.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import it.unibo.lam2026.parkmate.model.VeicoloRepository

class VeicoliViewModelFactory(private val repository: VeicoloRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(VeicoliViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return VeicoliViewModel(repository) as T
        }
        throw IllegalArgumentException("Classe ViewModel sconosciuta")
    }
}