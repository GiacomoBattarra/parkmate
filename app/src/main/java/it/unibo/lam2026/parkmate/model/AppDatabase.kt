package it.unibo.lam2026.parkmate.model

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// 1. MODIFICA QUI: Abbiamo sostituito Parcheggio::class con SessioneParcheggio::class
// e aumentato la versione a 6
@Database(entities = [SessioneParcheggio::class, Veicolo::class], version = 6, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    // Colleghiamo i nostri DAO
    abstract fun parcheggioDao(): ParcheggioDao
    abstract fun veicoloDao(): VeicoloDao

    // Questo blocco garantisce che esista UNA SOLA istanza del database in tutta l'app
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "parkmate_database" // Il nome del file fisico sul telefono
                )
                    // fallbackToDestructiveMigration cancella i vecchi dati se la versione cambia
                    // (Ottimo in fase di sviluppo per evitare crash)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}