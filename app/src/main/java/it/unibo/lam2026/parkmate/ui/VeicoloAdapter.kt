package it.unibo.lam2026.parkmate.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import it.unibo.lam2026.parkmate.databinding.ItemVeicoloBinding
import it.unibo.lam2026.parkmate.model.Veicolo

// L'Adapter accetta una lista di veicoli e una funzione da eseguire quando si preme "Elimina"
class VeicoloAdapter(
    private var listaVeicoli: List<Veicolo>,
    private val onEliminaClick: (Veicolo) -> Unit
) : RecyclerView.Adapter<VeicoloAdapter.VeicoloViewHolder>() {

    // Il ViewHolder è il contenitore della singola riga visiva
    inner class VeicoloViewHolder(val binding: ItemVeicoloBinding) : RecyclerView.ViewHolder(binding.root)

    // 1. Infla (disegna) il layout XML per la prima volta[cite: 15]
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VeicoloViewHolder {
        val binding = ItemVeicoloBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VeicoloViewHolder(binding)
    }

    // 2. Conta quanti elementi ci sono in totale[cite: 15]
    override fun getItemCount(): Int {
        return listaVeicoli.size
    }

    // 3. Riempie la singola riga con i dati giusti[cite: 15]
    override fun onBindViewHolder(holder: VeicoloViewHolder, position: Int) {
        val veicoloAttuale = listaVeicoli[position]

        // Popoliamo il testo
        holder.binding.txtNomeVeicolo.text = veicoloAttuale.nome
        holder.binding.txtTipoVeicolo.text = veicoloAttuale.tipo

        // Cambiamo l'icona in base al tipo (per ora usiamo icone di default Android)
        when (veicoloAttuale.tipo.lowercase()) {
            "auto" -> holder.binding.imgTipoVeicolo.setImageResource(android.R.drawable.ic_dialog_map)
            "moto" -> holder.binding.imgTipoVeicolo.setImageResource(android.R.drawable.ic_menu_directions)
            "bici" -> holder.binding.imgTipoVeicolo.setImageResource(android.R.drawable.ic_menu_compass)
        }

        // Ascoltatore per il bottone del cestino
        holder.binding.btnEliminaVeicolo.setOnClickListener {
            onEliminaClick(veicoloAttuale)
        }
    }

    // Funzione fondamentale per aggiornare la lista quando il Database cambia
    fun aggiornaDati(nuovaLista: List<Veicolo>) {
        listaVeicoli = nuovaLista
        notifyDataSetChanged() // Avvisa la RecyclerView di ridisegnarsi
    }
}