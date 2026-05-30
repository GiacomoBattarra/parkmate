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
    private val onPosizioneClick: (PosizioneSalvata) -> Unit,
    private val onEditClick: (PosizioneSalvata) -> Unit,    // <--- NUOVO: Per la matita
    private val onDeleteClick: (PosizioneSalvata) -> Unit   // <--- Per il cestino
) : RecyclerView.Adapter<PosizioniSalvateAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textNome: TextView = view.findViewById(R.id.text_nome_posizione)
        val btnModifica: TextView = view.findViewById(R.id.btn_modifica_posizione) // <--- MATITA
        val btnElimina: TextView = view.findViewById(R.id.btn_elimina_posizione)   // <--- CESTINO
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_posizione_salvata, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val posizione = posizioni[position]

        holder.textNome.text = posizione.nome

        // 1. Cliccando il nome, la mappa si sposta
        holder.textNome.setOnClickListener { onPosizioneClick(posizione) }

        // 2. Cliccando la matita, parte la modifica
        holder.btnModifica.setOnClickListener { onEditClick(posizione) }

        // 3. Cliccando il cestino, lo elimina
        holder.btnElimina.setOnClickListener { onDeleteClick(posizione) }
    }

    override fun getItemCount() = posizioni.size

    fun updateData(newPosizioni: List<PosizioneSalvata>) {
        this.posizioni = newPosizioni
        notifyDataSetChanged()
    }
}