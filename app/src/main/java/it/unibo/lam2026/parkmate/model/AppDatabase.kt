package it.unibo.lam2026.parkmate.model

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [SessioneParcheggio::class, Veicolo::class, PosizioneSalvata::class], version = 9, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun parcheggioDao(): ParcheggioDao
    abstract fun veicoloDao(): VeicoloDao

    abstract fun posizioneSalvataDao(): PosizioneSalvataDao

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
                    // Ricrea il database in caso di cambio versione (comporta la perdita dei dati)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}