package it.unibo.lam2026.parkmate.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.viewModels
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import it.unibo.lam2026.parkmate.databinding.FragmentParkBottomSheetBinding
import it.unibo.lam2026.parkmate.viewmodel.ParcheggioViewModel

class ParkBottomSheetFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentParkBottomSheetBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ParcheggioViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentParkBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. Create a list of fake vehicles for testing
        val fakeVehicles = listOf("Fiat Panda (Auto)", "Vespa (Moto)", "Graziella (Bici)")

        // 2. Create an ArrayAdapter to format the list for the Spinner
        // android.R.layout.simple_spinner_item is a default layout provided by Android
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            fakeVehicles
        )

        // 3. Specify the layout for the drop-down list (when the user clicks it)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

        // 4. Attach the adapter to the Spinner in our UI
        binding.spinnerVehicles.adapter = adapter

        // --- Logic for the Confirm Button ---
        binding.btnConfirmPark.setOnClickListener {

            // Get the selected vehicle from the Spinner
            val selectedVehicle = binding.spinnerVehicles.selectedItem.toString()

            // Determine which parking type was selected
            val parkingType = when (binding.radioGroupParkType.checkedRadioButtonId) {
                binding.radioFree.id -> "Libero"
                binding.radioHourly.id -> "A Pagamento (Orario)"
                binding.radioFixed.id -> "Ticket Fisso"
                else -> "Nessuno" // Fallback if nothing is selected
            }

            // Simple validation: make sure they selected a type
            if (parkingType == "Nessuno") {
                Toast.makeText(requireContext(), "Seleziona un tipo di sosta!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener // Stop execution here
            }

            // Estraiamo i dati dallo "zainetto" arguments.
            // Se per caso non ci sono, usiamo 0.0 come fallback
            val latReale = arguments?.getDouble("LATITUDINE") ?: 0.0
            val lonReale = arguments?.getDouble("LONGITUDINE") ?: 0.0

            // Chiamiamo il ViewModel usando finalmente le coordinate reali
            viewModel.salvaParcheggio(selectedVehicle, parkingType, latReale, lonReale)

            Toast.makeText(requireContext(), "Parcheggio salvato a Lat: $latReale", Toast.LENGTH_SHORT).show()
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}