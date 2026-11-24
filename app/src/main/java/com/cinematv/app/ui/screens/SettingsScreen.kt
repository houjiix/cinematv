package com.cinematv.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cinematv.app.data.remote.authorized.OfficialStreamingDirectory
import com.cinematv.app.domain.model.AppSettings
import com.cinematv.app.domain.model.SubtitlePreference
import com.cinematv.app.domain.model.SubtitleStyle
import com.cinematv.app.subtitles.ArabicSubtitleEngine
import com.cinematv.app.ui.components.InstagramContactCard
import com.cinematv.app.ui.theme.CinemaColors
import com.cinematv.app.ui.theme.CinemaSpacing
import com.cinematv.app.ui.viewmodel.JellyfinWizardState

@Composable
fun SettingsScreen(
    settings: AppSettings,
    jellyfinState: JellyfinWizardState,
    isTvDevice: Boolean,
    onSubtitlePrefChange: (SubtitlePreference) -> Unit,
    onSubtitleStyleChange: ((SubtitleStyle) -> SubtitleStyle) -> Unit,
    onDarkThemeChange: (Boolean) -> Unit,
    onAutoplayChange: (Boolean) -> Unit,
    onConnectJellyfin: (String, String, String) -> Unit,
    onDisconnectJellyfin: () -> Unit,
    onSaveApiKeys: (String, String) -> Unit,
    onClearHistory: () -> Unit
) {
    val context = LocalContext.current
    var jfUrl by remember(settings.jellyfinServerUrl) { mutableStateOf(settings.jellyfinServerUrl) }
    var jfUser by remember(settings.jellyfinUsername) { mutableStateOf(settings.jellyfinUsername) }
    var jfPass by remember { mutableStateOf("") }
    var tmdbKey by remember { mutableStateOf("") }
    var openSubKey by remember { mutableStateOf("") }

    val arabicPreviewText = remember {
        ArabicSubtitleEngine.formatBidiText(
            "مرحباً بك في CINEMA TV — اختبار الترجمة العربية المتصلة 2026 (١٠٠٪)!",
            forceRtl = true
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(if (isTvDevice) CinemaSpacing.tvOverscanHorizontal else CinemaSpacing.md),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item { Text("Settings", style = MaterialTheme.typography.headlineMedium) }

        // 1. Arabic Subtitle Preference & Live RTL Preview
        item {
            SettingsCard(title = "Arabic Subtitle Priority & Appearance") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SubtitlePreference.entries.forEach { pref ->
                        val active = settings.subtitlePreference == pref
                        Button(
                            onClick = { onSubtitlePrefChange(pref) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (active) CinemaColors.GoldAccent else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (active) CinemaColors.Obsidian else MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            Text(pref.label)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Font Size: ${settings.subtitleStyle.fontSizeSp.toInt()} sp")
                Slider(
                    value = settings.subtitleStyle.fontSizeSp,
                    onValueChange = { sz -> onSubtitleStyleChange { it.copy(fontSizeSp = sz) } },
                    valueRange = 14f..36f
                )
                Text("Background Opacity: ${(settings.subtitleStyle.backgroundOpacity * 100).toInt()}%")
                Slider(
                    value = settings.subtitleStyle.backgroundOpacity,
                    onValueChange = { op -> onSubtitleStyleChange { it.copy(backgroundOpacity = op) } },
                    valueRange = 0f..1f
                )
                Spacer(Modifier.height(8.dp))
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Color(0xFF090A0D).copy(alpha = settings.subtitleStyle.backgroundOpacity),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = arabicPreviewText,
                            color = Color(settings.subtitleStyle.textColorHex),
                            fontSize = settings.subtitleStyle.fontSizeSp.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        // 2. Jellyfin Personal Media Server Setup Wizard
        item {
            SettingsCard(title = "Jellyfin Media Server") {
                OutlinedTextField(
                    value = jfUrl,
                    onValueChange = { jfUrl = it },
                    label = { Text("Server URL (https://media.example.com)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = jfUser,
                    onValueChange = { jfUser = it },
                    label = { Text("Username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = jfPass,
                    onValueChange = { jfPass = it },
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        enabled = !jellyfinState.isTesting,
                        onClick = { onConnectJellyfin(jfUrl, jfUser, jfPass) }
                    ) {
                        Text(if (jellyfinState.isTesting) "Connecting..." else "Connect & Verify")
                    }
                    if (settings.jellyfinUsername.isNotBlank()) {
                        OutlinedButton(onClick = onDisconnectJellyfin) { Text("Disconnect") }
                    }
                }
                jellyfinState.statusMessage?.let { msg ->
                    Text(
                        text = msg,
                        color = if (jellyfinState.isError) CinemaColors.CrimsonAction else CinemaColors.GoldAccent,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        // 3. Player & Appearance
        item {
            SettingsCard(title = "Playback & Appearance") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Dark Cinematic Theme")
                    Switch(checked = settings.darkTheme, onCheckedChange = onDarkThemeChange)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Autoplay Next Episode")
                    Switch(checked = settings.autoplayNextEpisode, onCheckedChange = onAutoplayChange)
                }
                OutlinedButton(onClick = onClearHistory) { Text("Clear Watch History & Progress") }
            }
        }

        // 4. Optional Metadata & Subtitle API Keys (stored encrypted)
        item {
            SettingsCard(title = "Metadata & Subtitle APIs (Encrypted Storage)") {
                OutlinedTextField(
                    value = tmdbKey,
                    onValueChange = { tmdbKey = it },
                    label = { Text("TMDB API Key (Metadata only)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = openSubKey,
                    onValueChange = { openSubKey = it },
                    label = { Text("OpenSubtitles Official API Key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(onClick = { onSaveApiKeys(tmdbKey, openSubKey); tmdbKey = ""; openSubKey = "" }) {
                    Text("Save Keys Securely")
                }
            }
        }

        // 5. Official Streaming Apps Deep-Links
        item {
            SettingsCard(title = "Official Streaming Applications") {
                Text(
                    "Commercial services without open playback APIs launch directly in their official Android / Android TV apps.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OfficialStreamingDirectory.services.forEach { svc ->
                        OutlinedButton(onClick = {
                            val launch = context.packageManager.getLaunchIntentForPackage(svc.packageName)
                                ?: Intent(Intent.ACTION_VIEW, Uri.parse(svc.webUrl))
                            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            runCatching { context.startActivity(launch) }
                        }) {
                            Text(svc.name)
                        }
                    }
                }
            }
        }

        // 6. About & Contact (@houjix_)
        item {
            SettingsCard(title = "About CINEMA TV & Contact") {
                Text("CINEMA TV v1.0.0 · Package com.cinematv.app")
                Text(
                    "Adaptive Android Phone & Android TV streaming platform built with Kotlin, Jetpack Compose, and AndroidX Media3.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                InstagramContactCard()
            }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(CinemaSpacing.md),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}
