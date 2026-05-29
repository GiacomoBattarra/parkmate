package it.unibo.lam2026.parkmate.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import it.unibo.lam2026.parkmate.R
import it.unibo.lam2026.parkmate.model.PosizioneSalvata

class PosizioniSalvateAdapter(
    private var posizioni: List<PosizioneSalvata>,
    // Passiamo due funzioni: una per quando l'utente clicca il luogo, una per quando lo elimina
    private val onPosizioneClick: (PosizioneSalvata) -> Unit,
    private val onDeleteClick: (PosizioneSalvata) -> Unit
) : RecyclerView.Adapter<PosizioniSalvateAdapter.ViewHolder>() {

    // Il ViewHolder "cattura" gli elementi dell'XML per non doverli cercare ogni volta
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textNome: TextView = view.findViewById(R.id.text_nome_posizione)
        val textCoordinate: TextView = view.findViewById(R.id.text_coordinate_posizione)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        // Qui "gonfiamo" il tuo file XML per ogni riga
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_posizione_salvata, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val posizione = posizioni[position]

        // Inseriamo i dati nei TextView
        holder.textNome.text = posizione.nome
        holder.textCoordinate.text = "Lat: ${posizione.latitudine}, Lng: ${posizione.longitudine}"

        // Se clicco la riga normalmente -> uso la posizione
        holder.itemView.setOnClickListener { onPosizioneClick(posizione) }

        // Se tengo premuto a lungo sulla riga -> elimino la posizione
        holder.itemView.setOnLongClickListener {
            onDeleteClick(posizione)
            true
        }
    }

    override fun getItemCount() = posizioni.size

    // Questa funzione verrà chiamata dal ViewModel quando i dati nel database cambiano
    fun updateData(newPosizioni: List<PosizioneSalvata>) {
        this.posizioni = newPosizioni
        notifyDataSetChanged() // Avvisa la lista di ridisegnarsi
    }
}