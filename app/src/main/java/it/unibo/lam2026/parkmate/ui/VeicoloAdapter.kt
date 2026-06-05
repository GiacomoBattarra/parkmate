package it.unibo.lam2026.parkmate.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import it.unibo.lam2026.parkmate.databinding.ItemVeicoloBinding
import it.unibo.lam2026.parkmate.model.Veicolo
import it.unibo.lam2026.parkmate.R

class VeicoloAdapter(
    private var listaVeicoli: List<Veicolo>,
    private val onEliminaClick: (Veicolo) -> Unit,
    private val onModificaClick: (Veicolo) -> Unit,
    private val onParcheggioAttivoClick: (Veicolo) -> Unit // 👇 NUOVO: Ascoltatore per il click sulla P!
) : RecyclerView.Adapter<VeicoloAdapter.VeicoloViewHolder>() {

    private var veicoliParcheggiati: List<String> = emptyList()

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

        // CONTROLLO STATO PARCHEGGIO ATTIVO
        val stringaIdentificativa = "${veicoloAttuale.nome} (${veicoloAttuale.tipo})"

        if (veicoliParcheggiati.contains(stringaIdentificativa)) {
            holder.binding.imgStatoParcheggio.visibility = View.VISIBLE
        } else {
            holder.binding.imgStatoParcheggio.visibility = View.GONE
        }

        // 👇 NUOVO: Click sulla P 👇
        holder.binding.imgStatoParcheggio.setOnClickListener {
            onParcheggioAttivoClick(veicoloAttuale)
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

    fun aggiornaStatoParcheggi(nomiParcheggiati: List<String>) {
        this.veicoliParcheggiati = nomiParcheggiati
        notifyDataSetChanged()
    }
}