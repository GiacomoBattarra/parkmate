package it.unibo.lam2026.parkmate.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import it.unibo.lam2026.parkmate.R
import it.unibo.lam2026.parkmate.model.SessioneParcheggio

// Adapter responsabile del rendering delle singole sessioni di parcheggio all'interno della RecyclerView
class ParcheggiAdapter(private val lista: List<SessioneParcheggio>) :
    RecyclerView.Adapter<ParcheggiAdapter.ParcheggioViewHolder>() {

    class ParcheggioViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nome: TextView = view.findViewById(R.id.textNomeParcheggio)
        val tipo: TextView = view.findViewById(R.id.textTipoParcheggio)

        // Punto di estensione per la mappatura di futuri attributi visivi (es. tariffa oraria, posti disponibili)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ParcheggioViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_parcheggio, parent, false)
        return ParcheggioViewHolder(view)
    }

    override fun onBindViewHolder(holder: ParcheggioViewHolder, position: Int) {
        val parcheggio = lista[position]
        holder.nome.text = parcheggio.veicoloNome
        holder.tipo.text = parcheggio.tipoParcheggio
    }

    override fun getItemCount() = lista.size
}