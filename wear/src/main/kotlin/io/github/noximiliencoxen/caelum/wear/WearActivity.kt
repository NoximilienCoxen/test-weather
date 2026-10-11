package io.github.noximiliencoxen.caelum.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class WearActivity : ComponentActivity() {

    private val viewModel: WearViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val stato = viewModel.stato.collectAsStateWithLifecycle().value
            WearApp(stato)
        }
    }

    override fun onStart() {
        super.onStart()
        // Un aggiornamento a ogni apertura: l'orologio non ha un lavoro periodico (ancora).
        viewModel.aggiorna()
    }
}
