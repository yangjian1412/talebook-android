package com.talebook.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.talebook.app.data.repository.SettingsRepository
import com.talebook.app.viewmodel.SplashNavigation
import com.talebook.app.viewmodel.SplashViewModel

@Composable
fun SplashScreen(
    settingsRepository: SettingsRepository,
    onGoHome: () -> Unit,
    onGoLogin: () -> Unit,
) {
    val factory = object : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
            return SplashViewModel(settingsRepository) as T
        }
    }
    val viewModel: SplashViewModel = viewModel(factory = factory)

    LaunchedEffect(Unit) {
        viewModel.navigation.collect { nav ->
            when (nav) {
                is SplashNavigation.GoHome -> onGoHome()
                is SplashNavigation.GoLogin -> onGoLogin()
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.verify()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "TaleBook",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            fontSize = 36.sp
        )
    }
}
