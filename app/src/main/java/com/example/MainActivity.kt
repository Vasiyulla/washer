package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.ui.navigation.MainAppScaffold
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.WashViewModel

class MainActivity : ComponentActivity() {
  private val viewModel: WashViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        MainAppScaffold(viewModel = viewModel)
      }
    }
  }
}
