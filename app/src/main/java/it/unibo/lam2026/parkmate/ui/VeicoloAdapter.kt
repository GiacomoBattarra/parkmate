package it.unibo.lam2026.parkmate.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import it.unibo.lam2026.parkmate.databinding.ItemVeicoloBinding
import it.unibo.lam2026.parkmate.model.Veicolo
import it.unibo.lam2026.parkmate.R

class VeicoloAdapter(
    private var listaVeicoli: List<Veicolo>,
    private val onEliminaClick: (Veicolo) -> Unit,
    private val onModificaClick: (Veicolo) -> Unit
) : RecyclerView.Adapter<VeicoloAdapter.VeicoloViewHolder>() {

    inner class VeicoloViewHolder(val binding: ItemVeicoloBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VeicoloViewHolder {
        val binding = ItemVeicoloBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VeicoloViewHolder(binding)
    }

    override fun onBindViewHolder(holder: VeicoloViewHolder, position: Int) {
        val veicoloAttuale = listaVeicoli[position]

        holder.binding.txtNomeVeicolo.text = veicoloAttuale.nome
        holder.binding.txtTipoVeicolo.text = veicoloAttuale.tipo

        // Gestione icone
        when (veicoloAttuale.tipo.lowercase()) {
            "auto" -> holder.binding.imgTipoVeicolo.setImageResource(R.drawable.ic_car)
            "moto" -> holder.binding.imgTipoVeicolo.setImageResource(R.drawable.ic_motorcycle)
            "bici" -> holder.binding.imgTipoVeicolo.setImageResource(R.drawable.ic_bike)
        }

        // Click sul Cestino
        holder.binding.btnEliminaVeicolo.setOnClickListener {
            onEliminaClick(veicoloAttuale)
        }

        // Click sulla Matitina
        holder.binding.btnModificaVeicolo.setOnClickListener {
            onModificaClick(veicoloAttuale)
        }
    }

    override fun getItemCount(): Int = listaVeicoli.size

    fun aggiornaDati(nuovaLista: List<Veicolo>) {
        listaVeicoli = nuovaLista
        notifyDataSetChanged()
    }
}