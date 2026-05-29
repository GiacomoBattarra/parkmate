package it.unibo.lam2026.parkmate.model

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// Abbiamo unito tutto in un'unica riga e alzato la versione a 6!
@Database(entities = [SessioneParcheggio::class, Veicolo::class, PosizioneSalvata::class], version = 6, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    // I tuoi DAO esistenti
    abstract fun parcheggioDao(): ParcheggioDao
    abstract fun veicoloDao(): VeicoloDao

    // Il nuovo DAO per i preferiti
    abstract fun posizioneSalvataDao(): PosizioneSalvataDao

    // Blocco per il Singleton (Istanza unica)
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "parkmate_database" // Nome del file fisico
                )
                    // Questa riga ci salva la vita: cancella il vecchio DB e lo ricrea aggiornato
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}