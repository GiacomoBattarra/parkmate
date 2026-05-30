package it.unibo.lam2026.parkmate.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "posizioni_salvate")
data class PosizioneSalvata(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val nome: String,        // Es. "Casa", "Lavoro", "Palestra"
    val latitudine: Double,  // Coordinata X per la mappa
    val longitudine: Double  // Coordinata Y per la mappa
)