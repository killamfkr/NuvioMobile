package com.nuvio.app.features.profiles

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.NuvioInputField
import com.nuvio.app.core.ui.NuvioPrimaryButton
import com.nuvio.app.core.ui.NuvioScreen
import com.nuvio.app.core.ui.NuvioScreenHeader
import com.nuvio.app.core.ui.NuvioSectionLabel
import com.nuvio.app.core.ui.NuvioStatusModal
import com.nuvio.app.core.ui.NuvioSurfaceCard
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileEditScreen(
    profile: NuvioProfile? = null,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isNew = profile == null
    val scope = rememberCoroutineScope()

    var name by rememberSaveable { mutableStateOf(profile?.name ?: "") }
    var selectedColor by rememberSaveable { mutableStateOf(profile?.avatarColorHex ?: PROFILE_COLORS.first()) }
    var usesPrimaryAddons by rememberSaveable { mutableStateOf(profile?.usesPrimaryAddons ?: false) }
    var isSaving by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showPinSetup by remember { mutableStateOf(false) }
    var showPinClear by remember { mutableStateOf(false) }

    NuvioScreen(modifier = modifier) {
        stickyHeader {
            NuvioScreenHeader(
                title = if (isNew) "Add Profile" else "Edit Profile",
                onBack = onBack,
            )
        }

        item {
            NuvioSurfaceCard {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val color = remember(selectedColor) { parseHexColor(selectedColor) }
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(color.copy(alpha = 0.2f))
                            .border(3.dp, color.copy(alpha = 0.6f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (name.isNotBlank()) {
                            Text(
                                text = name.take(1).uppercase(),
                                style = MaterialTheme.typography.displayLarge,
                                color = color,
                                fontWeight = FontWeight.Bold,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Person,
                                contentDescription = null,
                                tint = color,
                                modifier = Modifier.size(48.dp),
                            )
                        }
                    }
                }
            }
        }

        item {
            NuvioSectionLabel(text = "NAME")
        }
        item {
            NuvioSurfaceCard {
                NuvioInputField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = "Profile name",
                )
            }
        }

        item {
            NuvioSectionLabel(text = "COLOR")
        }
        item {
            NuvioSurfaceCard {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    PROFILE_COLORS.forEach { hex ->
                        val color = remember(hex) { parseHexColor(hex) }
                        val isSelected = hex == selectedColor
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(color)
                                .then(
                                    if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                    else Modifier
                                )
                                .clickable { selectedColor = hex },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            NuvioSectionLabel(text = "OPTIONS")
        }
        item {
            NuvioSurfaceCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Use Primary Addons",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = "Share addons with the main profile",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = usesPrimaryAddons,
                        onCheckedChange = { usesPrimaryAddons = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            uncheckedTrackColor = MaterialTheme.colorScheme.outlineVariant,
                        ),
                    )
                }
            }
        }

        if (!isNew) {
            item {
                NuvioSectionLabel(text = "SECURITY")
            }
            item {
                NuvioSurfaceCard {
                    if (profile?.pinEnabled == true) {
                        NuvioPrimaryButton(
                            text = "Remove PIN Lock",
                            onClick = { showPinClear = true },
                        )
                    } else {
                        NuvioPrimaryButton(
                            text = "Set PIN Lock",
                            onClick = { showPinSetup = true },
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            NuvioPrimaryButton(
                text = if (isSaving) "Saving..." else "Save",
                enabled = name.isNotBlank() && !isSaving,
                onClick = {
                    isSaving = true
                    scope.launch {
                        if (isNew) {
                            ProfileRepository.createProfile(
                                name = name,
                                avatarColorHex = selectedColor,
                                usesPrimaryAddons = usesPrimaryAddons,
                            )
                        } else {
                            ProfileRepository.updateProfile(
                                profileIndex = profile!!.profileIndex,
                                name = name,
                                avatarColorHex = selectedColor,
                                usesPrimaryAddons = usesPrimaryAddons,
                                avatarId = profile.avatarId,
                            )
                        }
                        isSaving = false
                        onSaved()
                    }
                },
            )
        }

        if (!isNew && (profile?.profileIndex ?: 0) > 1) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(
                        text = "Delete Profile",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }

    NuvioStatusModal(
        title = "Delete Profile?",
        message = "All data for \"${profile?.name}\" will be permanently deleted.",
        isVisible = showDeleteConfirm,
        confirmText = "Delete",
        dismissText = "Cancel",
        onConfirm = {
            showDeleteConfirm = false
            scope.launch {
                profile?.let { ProfileRepository.deleteProfile(it.profileIndex) }
                onBack()
            }
        },
        onDismiss = { showDeleteConfirm = false },
    )

    if (showPinSetup && profile != null) {
        PinSetupDialog(
            profileIndex = profile.profileIndex,
            hasExistingPin = profile.pinEnabled,
            onDone = {
                showPinSetup = false
                scope.launch { ProfileRepository.pullProfiles() }
            },
            onDismiss = { showPinSetup = false },
        )
    }

    if (showPinClear && profile != null) {
        PinEntryDialog(
            profileName = "Remove PIN for ${profile.name}",
            onVerify = { pin ->
                scope.launch {
                    ProfileRepository.clearPin(profile.profileIndex, pin)
                }
                PinVerifyResult(unlocked = true)
            },
            onDismiss = {
                showPinClear = false
                scope.launch { ProfileRepository.pullProfiles() }
            },
        )
    }
}

@Composable
fun PinSetupDialog(
    profileIndex: Int,
    hasExistingPin: Boolean,
    onDone: () -> Unit,
    onDismiss: () -> Unit,
) {
    var step by remember { mutableStateOf(if (hasExistingPin) "current" else "new") }
    var currentPin by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    when (step) {
        "current" -> PinEntryDialog(
            profileName = "Enter current PIN",
            onVerify = { pin ->
                val result = ProfileRepository.verifyPin(profileIndex, pin)
                if (result.unlocked) {
                    currentPin = pin
                    step = "new"
                }
                result
            },
            onDismiss = onDismiss,
        )
        "new" -> PinEntryDialog(
            profileName = "Enter new PIN",
            onVerify = { pin ->
                scope.launch {
                    ProfileRepository.setPin(
                        profileIndex = profileIndex,
                        pin = pin,
                        currentPin = currentPin.ifEmpty { null },
                    )
                }
                onDone()
                PinVerifyResult(unlocked = true)
            },
            onDismiss = onDismiss,
        )
    }
}
