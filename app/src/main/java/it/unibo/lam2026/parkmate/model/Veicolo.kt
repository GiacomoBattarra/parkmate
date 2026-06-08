package it.unibo.lam2026.parkmate.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tabella_veicoli")
data class Veicolo(
    // autogenerate = true fa sì che Room crei un ID 1, 2, 3 automaticamente
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val nome: String,
    val tipo: String
)