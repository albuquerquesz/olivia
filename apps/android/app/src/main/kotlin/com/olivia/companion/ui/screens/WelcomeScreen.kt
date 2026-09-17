package com.olivia.companion.ui.screens

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

private const val OnboardingPageCount = 3

@Composable
fun OliviaWelcomeScreen(
    audioPermissionGranted: Boolean,
    bluetoothPermissionRequired: Boolean,
    bluetoothPermissionGranted: Boolean,
    onRequestPermissions: () -> Unit,
    onEnter: () -> Unit,
) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    val permissionsGranted = audioPermissionGranted &&
        (!bluetoothPermissionRequired || bluetoothPermissionGranted)

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
            AnimatedContent(
                targetState = page,
                label = "onboarding page",
            ) { currentPage ->
                when (currentPage) {
                    0 -> OnboardingIntro()
                    1 -> OnboardingHowItWorks()
                    else -> OnboardingPermissions(
                        audioPermissionGranted = audioPermissionGranted,
                        bluetoothPermissionRequired = bluetoothPermissionRequired,
                        bluetoothPermissionGranted = bluetoothPermissionGranted,
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
            OnboardingPageIndicator(page)
            Button(
                onClick = {
                    when {
                        page < OnboardingPageCount - 1 -> page += 1
                        permissionsGranted -> onEnter()
                        else -> onRequestPermissions()
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
                    text = onboardingButtonLabel(page, permissionsGranted),
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
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "Companion para seus takes",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        text = "Olivia",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                }
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
            description = "A Olivia observa enquadramento, luz e horizonte enquanto você trabalha, e orienta tudo por áudio.",
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
private fun OnboardingPermissions(
    audioPermissionGranted: Boolean,
    bluetoothPermissionRequired: Boolean,
    bluetoothPermissionGranted: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        OnboardingHeading(
            title = "Só o necessário",
            description = "A Olivia precisa de acesso ao microfone para ouvir seus comandos. Nos óculos reais, também do Bluetooth para fazer a ponte.",
        )
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OnboardingPermissionRow(
                icon = Icons.Outlined.Mic,
                title = "Microfone do celular",
                description = "Ouve “Oi, Olivia” e os comandos locais.",
                granted = audioPermissionGranted,
            )
            if (bluetoothPermissionRequired) {
                OnboardingPermissionRow(
                    icon = Icons.Outlined.Bluetooth,
                    title = "Bluetooth",
                    description = "Conecta o celular aos Ray-Ban Meta.",
                    granted = bluetoothPermissionGranted,
                )
            } else {
                OnboardingInfoRow(
                    icon = Icons.Outlined.Bluetooth,
                    title = "Autorização dos óculos",
                    description = "O pareamento e a autorização acontecem no app Meta AI.",
                )
            }
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
                    text = "Sem câmera do celular, localização ou notificações.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun OnboardingHeading(title: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold,
            ),
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
private fun OnboardingInfoRow(
    icon: ImageVector,
    title: String,
    description: String,
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
            Text(
                text = "Meta AI",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
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
private fun OnboardingPageIndicator(page: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(OnboardingPageCount) { index ->
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

private fun onboardingButtonLabel(page: Int, permissionsGranted: Boolean): String = when {
    page == 0 -> "Começar"
    page == 1 -> "Continuar"
    permissionsGranted -> "Entrar na Olivia"
    else -> "Permitir e continuar"
}
