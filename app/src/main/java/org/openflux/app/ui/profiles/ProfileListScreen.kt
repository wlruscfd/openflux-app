package org.openflux.app.ui.profiles

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.openflux.app.LocalOpenFluxApp
import org.openflux.app.R
import org.openflux.app.data.Profile
import org.openflux.app.data.ProfileDeepLink
import org.openflux.app.data.ProfileRepository
import org.openflux.app.data.SettingsRepository

internal data class ProfileListState(
    val profiles: List<Profile> = emptyList(),
    val activeProfileId: String? = null,
)

class ProfileListViewModel(
    private val profileRepository: ProfileRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    internal val state: StateFlow<ProfileListState> = combine(
        profileRepository.observeAll(),
        settingsRepository.activeProfileId,
    ) { profiles, activeId -> ProfileListState(profiles, activeId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProfileListState())

    fun setActive(id: String) {
        viewModelScope.launch { settingsRepository.setActiveProfileId(id) }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            profileRepository.delete(id)
            if (state.value.activeProfileId == id) settingsRepository.setActiveProfileId(null)
        }
    }

    /** Saves an imported profile (from a deep link / QR / clipboard) as-is. */
    fun saveImported(profile: Profile) {
        viewModelScope.launch { profileRepository.save(profile) }
    }
}

@Composable
fun ProfileListScreen(
    onAddProfile: () -> Unit,
    onScanQr: () -> Unit,
    onEditProfile: (String) -> Unit,
) {
    val app = LocalOpenFluxApp.current
    val viewModel: ProfileListViewModel = viewModel(
        factory = viewModelFactory {
            initializer { ProfileListViewModel(app.profileRepository, app.settingsRepository) }
        },
    )
    val state by viewModel.state.collectAsState()

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var fabExpanded by remember { mutableStateOf(false) }
    var shareMenuProfile by remember { mutableStateOf<Profile?>(null) }
    var qrProfile by remember { mutableStateOf<Profile?>(null) }

    Scaffold(
        floatingActionButton = {
            AddProfileFabMenu(
                expanded = fabExpanded,
                onExpandedChange = { fabExpanded = it },
                onAddManually = onAddProfile,
                onAddFromClipboard = {
                    // A profile deep link copied from the admin panel/API is
                    // the common "add" source - see ProfileDeepLink.kt. If
                    // the clipboard has one, add it straight away; otherwise
                    // tell the user instead of silently opening a blank form.
                    val clipboardText = clipboardManager.getText()?.text
                    val profile = clipboardText?.let { ProfileDeepLink.parse(Uri.parse(it)) }
                    if (profile != null) {
                        viewModel.saveImported(profile)
                        Toast.makeText(
                            context,
                            context.getString(R.string.profiles_clipboard_added),
                            Toast.LENGTH_SHORT,
                        ).show()
                    } else {
                        Toast.makeText(
                            context,
                            context.getString(R.string.profiles_clipboard_invalid),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
                onScanQr = onScanQr,
            )
        },
    ) { padding ->
        // Transparent click-catcher (no visual scrim): while the FAB menu is
        // open, a tap on any free space collapses it. The FAB and its menu
        // items live in Scaffold's FAB layer, drawn above this content box,
        // so they keep receiving their own taps.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .clickable(enabled = fabExpanded) { fabExpanded = false },
        ) {
            if (state.profiles.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.profiles_empty))
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(state.profiles, key = { it.id }) { profile ->
                        ProfileRow(
                            profile = profile,
                            active = profile.id == state.activeProfileId,
                            onSelect = { viewModel.setActive(profile.id) },
                            onEdit = { onEditProfile(profile.id) },
                            onShare = { shareMenuProfile = profile },
                            onDelete = { viewModel.delete(profile.id) },
                        )
                    }
                }
            }
        }
    }

    if (shareMenuProfile != null) {
        ProfileShareMenuDialog(
            onExportClipboard = {
                shareMenuProfile?.let { profile ->
                    clipboardManager.setText(AnnotatedString(ProfileDeepLink.buildUri(profile).toString()))
                    Toast.makeText(
                        context,
                        context.getString(R.string.profile_share_clipboard_copied),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
                shareMenuProfile = null
            },
            onShowQr = {
                qrProfile = shareMenuProfile
                shareMenuProfile = null
            },
            onDismiss = { shareMenuProfile = null },
        )
    }
    qrProfile?.let { profile ->
        ShareProfileDialog(profile = profile, onDismiss = { qrProfile = null })
    }
}