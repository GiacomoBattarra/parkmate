package it.unibo.lam2026.parkmate.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import it.unibo.lam2026.parkmate.model.SessioneParcheggio
import it.unibo.lam2026.parkmate.databinding.ItemHistoryBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryAdapter(
    private var storicoList: List<SessioneParcheggio>,
    private val onTerminaClick: (SessioneParcheggio) -> Unit,
    private val onEliminaClick: (SessioneParcheggio) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

    class HistoryViewHolder(val binding: ItemHistoryBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val binding = ItemHistoryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return HistoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val sessione = storicoList[position]

        // --- INIZIO MODIFICA: SCRITTA ROSSA HTML PER I VEICOLI ELIMINATI ---
        val isEliminato = sessione.veicoloNome.contains("[ELIMINATO]")
        val nomeReale = sessione.veicoloNome.replace(" [ELIMINATO]", "")

        if (isEliminato) {
            @Suppress("DEPRECATION")
            val testoHtml = "Veicolo: $nomeReale <br> <font color='#D32F2F'><b>(❌ VEICOLO ELIMINATO)</b></font>"
            holder.binding.tvHistoryVehicle.text = android.text.Html.fromHtml(testoHtml)
        } else {
            holder.binding.tvHistoryVehicle.text = "Veicolo: $nomeReale"
        }
        // --- FINE MODIFICA ---

        holder.binding.tvHistoryType.text = "Tipo: ${sessione.tipoParcheggio}"

        val formattaData = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val dataInizio = formattaData.format(Date(sessione.startTimeStamp))

        // CONTROLLO STATO: ATTIVO vs TERMINATO
        if (sessione.isAttivo) {
            // --- PARCHEGGIO IN CORSO ---
            holder.binding.tvHistoryTime.text = "Orario: $dataInizio - In corso"
            holder.binding.btnTerminaParcheggio.visibility = View.VISIBLE
            holder.binding.btnEliminaParcheggio.visibility = View.GONE

            // Diciamo all'utente che stiamo conteggiando il costo
            holder.binding.tvHistoryCost.text = "Costo: Calcolo al termine..."

        } else {
            // --- PARCHEGGIO TERMINATO ---
            val dataFine = if (sessione.endTimeStamp != null) formattaData.format(Date(sessione.endTimeStamp)) else "N/A"
            holder.binding.tvHistoryTime.text = "Orario: $dataInizio - $dataFine"
            holder.binding.btnTerminaParcheggio.visibility = View.GONE
            holder.binding.btnEliminaParcheggio.visibility = View.VISIBLE

            // Se è chiuso, mostriamo il costo. Se è 0, mostriamo la parola "Gratis"!
            if (sessione.costoTotale != null) {
                if (sessione.costoTotale == 0.0) {
                    holder.binding.tvHistoryCost.text = "Costo: Gratis"
                } else {
                    holder.binding.tvHistoryCost.text = String.format(Locale.getDefault(), "Costo: €%.2f", sessione.costoTotale)
                }
            } else {
                holder.binding.tvHistoryCost.text = "Costo: N/A"
            }
        }

        // AZIONI DEI BOTTONI
        holder.binding.btnTerminaParcheggio.setOnClickListener {
            onTerminaClick(sessione)
        }
        holder.binding.btnEliminaParcheggio.setOnClickListener {
            onEliminaClick(sessione)
        }

        // Mostriamo un testo di caricamento
        holder.binding.tvHistoryLocation.text = "📍 Ricerca indirizzo in corso..."

        // Lanciamo un processo in background per tradurre le coordinate
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                val geocoder = android.location.Geocoder(holder.itemView.context, Locale.getDefault())
                val indirizzi = geocoder.getFromLocation(sessione.latitudine, sessione.longitudine, 1)

                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    if (!indirizzi.isNullOrEmpty()) {
                        val addr = indirizzi[0]
                        val via = addr.thoroughfare ?: ""
                        val civico = addr.subThoroughfare ?: ""
                        val citta = addr.locality ?: ""

                        // Formattiamo l'indirizzo in modo pulito
                        val indirizzoPulito = if (via.isNotEmpty() && citta.isNotEmpty()) {
                            if (civico.isNotEmpty()) "$via $civico, $citta" else "$via, $citta"
                        } else {
                            addr.getAddressLine(0) ?: "Indirizzo sconosciuto"
                        }

                        holder.binding.tvHistoryLocation.text = "📍 $indirizzoPulito"
                    } else {
                        // Fallback in caso di via non trovata
                        val latCorta = String.format(Locale.getDefault(), "%.4f", sessione.latitudine)
                        val lonCorta = String.format(Locale.getDefault(), "%.4f", sessione.longitudine)
                        holder.binding.tvHistoryLocation.text = "📍 Non riesco a caricare l'indirizzo\n📍 Coord: $latCorta, $lonCorta"
                    }
                }
            } catch (e: Exception) {
                // Fallback in caso di assenza di rete internet
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    val latCorta = String.format(Locale.getDefault(), "%.4f", sessione.latitudine)
                    val lonCorta = String.format(Locale.getDefault(), "%.4f", sessione.longitudine)
                    holder.binding.tvHistoryLocation.text = "📍 Non riesco a caricare l'indirizzo\n📍 Coord: $latCorta, $lonCorta"
                }
            }
        }
    }

    override fun getItemCount(): Int = storicoList.size

    fun aggiornaDati(nuovaLista: List<SessioneParcheggio>) {
        storicoList = nuovaLista
        notifyDataSetChanged()
    }
}