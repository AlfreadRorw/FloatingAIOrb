package com.alf.pet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.alf.pet.ui.AlfPetTheme
import com.alf.pet.ui.PetScreen

class MainActivity : ComponentActivity() {

    private val viewModel: PetViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AlfPetTheme {
                PetScreen(viewModel)
            }
        }
    }

    override fun onStop() {
        viewModel.saveNow()
        super.onStop()
    }
}
