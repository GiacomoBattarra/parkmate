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
    private val onParcheggioAttivoClick: (Veicolo) -> Unit
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

        // Assegnazione dinamica dell'icona in base alla categoria del veicolo
        when (veicoloAttuale.tipo.lowercase()) {
            "auto" -> holder.binding.imgTipoVeicolo.setImageResource(R.drawable.ic_car)
            "moto" -> holder.binding.imgTipoVeicolo.setImageResource(R.drawable.ic_motorcycle)
            "bici" -> holder.binding.imgTipoVeicolo.setImageResource(R.drawable.ic_bike)
        }

        // Verifica e aggiornamento visivo dello stato di parcheggio (sosta in corso)
        val stringaIdentificativa = "${veicoloAttuale.nome} (${veicoloAttuale.tipo})"

        if (veicoliParcheggiati.contains(stringaIdentificativa)) {
            holder.binding.imgStatoParcheggio.visibility = View.VISIBLE
        } else {
            holder.binding.imgStatoParcheggio.visibility = View.GONE
        }

        // Callback per la visualizzazione dei dettagli della sosta attiva
        holder.binding.imgStatoParcheggio.setOnClickListener {
            onParcheggioAttivoClick(veicoloAttuale)
        }

        // Callback per la richiesta di eliminazione del veicolo dal database
        holder.binding.btnEliminaVeicolo.setOnClickListener {
            onEliminaClick(veicoloAttuale)
        }

        // Callback per l'apertura del modulo di modifica anagrafica del veicolo
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