package it.unibo.lam2026.parkmate.model

import androidx.room.Entity
import androidx.room.PrimaryKey

// Diciamo a Room che questa è una tabella chiamata "tabella_parcheggi"
@Entity(tableName = "tabella_parcheggi")
data class Parcheggio(
    // Ogni tabella ha bisogno di una chiave primaria unica!
    @PrimaryKey
    val id: String,
    val nome: String,
    val postiDisponibili: Int,
    val tariffaOraria: Double,
    val latitudine: Double,
    val longitudine: Double
)