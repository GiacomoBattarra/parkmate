package it.unibo.lam2026.parkmate.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tabella_sessioni_parcheggio")
data class SessioneParcheggio(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val veicoloNome: String,  // Colleghiamo il nome del veicolo
    val tipoParcheggio: String, // "Libero", "Orario", "Fisso"
    val latitudine: Double,
    val longitudine: Double,
    val startTimeStamp: Long, // Orario di inizio in millisecondi

    // I campi con "?" sono opzionali, si riempiono solo se l'utente li inserisce o quando chiude il parcheggio
    val endTimeStamp: Long? = null,
    val costoTotale: Double? = null,
    val nota: String? = null,
    val fotoPath: String? = null, // Percorso del file immagine sul telefono

    val isAttivo: Boolean = true, // Di default, quando crei il record, il parcheggio è attivo

    @ColumnInfo(name = "archiviato") val archiviato: Boolean = false
)