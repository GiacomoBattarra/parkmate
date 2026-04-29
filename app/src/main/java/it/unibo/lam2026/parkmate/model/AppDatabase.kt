package it.unibo.lam2026.parkmate.model

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// Diciamo a Room quali tabelle (entities) contiene questo database e la versione attuale
@Database(entities = [Parcheggio::class], version = 2)
abstract class AppDatabase : RoomDatabase() {

    // Colleghiamo il nostro DAO
    abstract fun parcheggioDao(): ParcheggioDao

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
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }

}