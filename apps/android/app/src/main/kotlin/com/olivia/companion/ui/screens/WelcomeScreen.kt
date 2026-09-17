package com.olivia.companion.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.olivia.companion.R

private const val IntroPage = 0
private const val HowItWorksPage = 1
private const val MicrophonePermissionPage = 2
private const val BluetoothPermissionPage = 3

private fun onboardingPageCount(bluetoothPermissionRequired: Boolean): Int =
    if (bluetoothPermissionRequired) BluetoothPermissionPage + 1 else MicrophonePermissionPage + 1

@Composable
fun OliviaWelcomeScreen(
    audioPermissionGranted: Boolean,
    bluetoothPermissionRequired: Boolean,
    bluetoothPermissionGranted: Boolean,
    onRequestAudioPermission: () -> Unit,
    onRequestBluetoothPermission: () -> Unit,
    onEnter: () -> Unit,
) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    val pageCount = onboardingPageCount(bluetoothPermissionRequired)
    val currentPage = page.coerceIn(0, pageCount - 1)
    val needsAudioPermission = currentPage == MicrophonePermissionPage && !audioPermissionGranted
    val needsBluetoothPermission = currentPage == BluetoothPermissionPage && !bluetoothPermissionGranted

    BackHandler(enabled = currentPage > IntroPage) {
        page = currentPage - 1
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp)
                .padding(bottom = 124.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            if (currentPage > IntroPage) {
                IconButton(
                    onClick = { page = currentPage - 1 },
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.surface, CircleShape),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Voltar",
                    )
                }
            }
            AnimatedContent(
                targetState = currentPage,
                label = "onboarding page",
            ) { currentPage ->
                when (currentPage) {
                    IntroPage -> OnboardingIntro()
                    HowItWorksPage -> OnboardingHowItWorks()
                    MicrophonePermissionPage -> OnboardingPermissionPage(
                        icon = Icons.Outlined.Mic,
                        title = "Dê voz à Olivia",
                        description = "O microfone do celular ouve “Oi, Olivia” e seus comandos locais.",
                        permissionTitle = "Microfone do celular",
                        permissionDescription = "Necessário para iniciar e controlar um take por voz.",
                        granted = audioPermissionGranted,
                        privacyNote = "O áudio do take não alimenta a IA.",
                    )
                    else -> OnboardingPermissionPage(
                        icon = Icons.Outlined.Bluetooth,
                        title = "Conecte seus óculos",
                        description = "O celular faz a ponte com os Ray-Ban Meta durante o take.",
                        permissionTitle = "Bluetooth",
                        permissionDescription = "Necessário para conexão e reconexão com os óculos.",
                        granted = bluetoothPermissionGranted,
                        privacyNote = "O pareamento e a autorização acontecem no app Meta AI.",
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OnboardingPageIndicator(page = currentPage, pageCount = pageCount)
            Button(
                onClick = {
                    when {
                        needsAudioPermission -> onRequestAudioPermission()
                        needsBluetoothPermission -> onRequestBluetoothPermission()
                        currentPage < pageCount - 1 -> page = currentPage + 1
                        else -> onEnter()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text(
                    text = onboardingButtonLabel(
                        page = currentPage,
                        pageCount = pageCount,
                        audioPermissionGranted = audioPermissionGranted,
                        bluetoothPermissionGranted = bluetoothPermissionGranted,
                    ),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun OnboardingIntro() {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 260.dp, max = 320.dp),
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = RoundedCornerShape(28.dp),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Image(
                    painter = painterResource(R.drawable.olivia_logo),
                    contentDescription = "Olivia",
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(236.dp)
                        .clip(RoundedCornerShape(24.dp)),
                    contentScale = ContentScale.Crop,
                )
            }
        }
        OnboardingHeading(
            title = "Grave com as mãos livres",
            description = "A Olivia observa o enquadramento, a luz e o horizonte, e orienta você por áudio.",
        )
    }
}

@Composable
private fun OnboardingHowItWorks() {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        OnboardingHeading(
            title = "Como funciona",
            description = "Um ciclo simples, feito para você não precisar parar o trabalho.",
        )
        Column {
            OnboardingStep(
                number = "01",
                icon = Icons.Outlined.Bluetooth,
                title = "Conecte os Ray-Ban Meta",
                description = "O celular faz a ponte com os óculos.",
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            OnboardingStep(
                number = "02",
                icon = Icons.Outlined.Videocam,
                title = "A Olivia analisa",
                description = "Um frame a cada 2 segundos, cuidando da bateria.",
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            OnboardingStep(
                number = "03",
                icon = Icons.AutoMirrored.Outlined.VolumeUp,
                title = "Você recebe por áudio",
                description = "Correções curtas, sem tocar no celular.",
            )
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            shape = RoundedCornerShape(20.dp),
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(
                    text = "Os frames de análise são descartados. O áudio do take não alimenta a IA.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun OnboardingPermissionPage(
    icon: ImageVector,
    title: String,
    description: String,
    permissionTitle: String,
    permissionDescription: String,
    granted: Boolean,
    privacyNote: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        OnboardingHeading(
            title = title,
            description = description,
        )
        OnboardingPermissionRow(
            icon = icon,
            title = permissionTitle,
            description = permissionDescription,
            granted = granted,
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            shape = RoundedCornerShape(20.dp),
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(
                    text = privacyNote,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun OnboardingHeading(title: String, description: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold,
            ),
        )
        description?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun OnboardingStep(
    number: String,
    icon: ImageVector,
    title: String,
    description: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = number,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun OnboardingPermissionRow(
    icon: ImageVector,
    title: String,
    description: String,
    granted: Boolean,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PermissionIcon(icon)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            PermissionStatus(granted = granted)
        }
    }
}

@Composable
private fun PermissionIcon(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun PermissionStatus(granted: Boolean) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (granted) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = "Concedida",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            text = if (granted) "Concedida" else "Necessária",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun OnboardingPageIndicator(page: Int, pageCount: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pageCount) { index ->
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .size(width = if (page == index) 24.dp else 8.dp, height = 8.dp)
                    .background(
                        color = if (page == index) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                        shape = CircleShape,
                    ),
            )
        }
    }
}

private fun onboardingButtonLabel(
    page: Int,
    pageCount: Int,
    audioPermissionGranted: Boolean,
    bluetoothPermissionGranted: Boolean,
): String = when {
    page == IntroPage -> "Começar"
    page == HowItWorksPage -> "Continuar"
    page == MicrophonePermissionPage && !audioPermissionGranted -> "Permitir microfone"
    page == MicrophonePermissionPage && page < pageCount - 1 -> "Continuar"
    page == BluetoothPermissionPage && !bluetoothPermissionGranted -> "Permitir Bluetooth"
    else -> "Entrar na Olivia"
}
