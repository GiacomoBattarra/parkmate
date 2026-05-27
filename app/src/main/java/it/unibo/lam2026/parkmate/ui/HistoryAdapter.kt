package it.unibo.lam2026.parkmate.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import it.unibo.lam2026.parkmate.model.SessioneParcheggio
import it.unibo.lam2026.parkmate.databinding.ItemHistoryBinding // <-- Ho aggiunto questo import!
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryAdapter(
    private var storicoList: List<SessioneParcheggio>
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

        holder.binding.tvHistoryVehicle.text = "Veicolo: ${sessione.veicoloNome}"
        holder.binding.tvHistoryType.text = "Tipo: ${sessione.tipoParcheggio}"

        val formattaData = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val dataInizio = formattaData.format(Date(sessione.startTimeStamp))

        // CORRETTO: ho messo "if" al posto di "se", e aggiunto "!!" per dire a Kotlin che il dato non è nullo
        val dataFine = if (sessione.endTimeStamp != null) {
            formattaData.format(Date(sessione.endTimeStamp!!))
        } else {
            "In corso"
        }
        holder.binding.tvHistoryTime.text = "Orario: $dataInizio - $dataFine"

        holder.binding.tvHistoryLocation.text = "Coordinate: ${sessione.latitudine}, ${sessione.longitudine}"

        if (sessione.costoTotale != null && sessione.costoTotale > 0.0) {
            holder.binding.tvHistoryCost.text = String.format("Costo: €%.2f", sessione.costoTotale)
        } else {
            holder.binding.tvHistoryCost.text = "Costo: N/A"
        }
    }

    override fun getItemCount(): Int = storicoList.size

    fun aggiornaDati(nuovaLista: List<SessioneParcheggio>) {
        storicoList = nuovaLista
        notifyDataSetChanged()
    }
}