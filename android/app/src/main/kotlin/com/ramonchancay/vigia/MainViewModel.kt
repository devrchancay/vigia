package com.ramonchancay.vigia

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ramonchancay.vigia.core.server.security.ServerTokenStore
import com.ramonchancay.vigia.feature.panel.navigation.PanelDestination
import com.ramonchancay.vigia.feature.setup.navigation.SetupDestination
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class MainViewModel @Inject constructor(tokenStore: ServerTokenStore) : ViewModel() {
    /** Where the app opens: setup until the owner has seen it once, then the panel. Null while loading. */
    val startDestination: StateFlow<Any?> = flow {
        emit(if (tokenStore.setupCompleted.first()) PanelDestination else SetupDestination)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
}
