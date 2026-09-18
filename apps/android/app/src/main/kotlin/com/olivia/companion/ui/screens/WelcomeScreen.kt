package com.olivia.companion.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Videocam
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
import com.olivia.companion.ui.OliviaButton
import com.olivia.companion.ui.OliviaButtonVariant

private const val IntroPage = 0
private const val HowItWorksPage = 1
private const val PermissionPage = 2
private const val OnboardingPageCount = 3

private enum class OnboardingPermission {
    Microphone,
    Bluetooth,
}

private fun pendingPermission(
    audioPermissionGranted: Boolean,
    bluetoothPermissionRequired: Boolean,
    bluetoothPermissionGranted: Boolean,
): OnboardingPermission? = when {
    !audioPermissionGranted -> OnboardingPermission.Microphone
    bluetoothPermissionRequired && !bluetoothPermissionGranted -> OnboardingPermission.Bluetooth
    else -> null
}

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
    val pageCount = OnboardingPageCount
    val currentPage = page.coerceIn(0, pageCount - 1)
    val permission = pendingPermission(
        audioPermissionGranted = audioPermissionGranted,
        bluetoothPermissionRequired = bluetoothPermissionRequired,
        bluetoothPermissionGranted = bluetoothPermissionGranted,
    ).takeIf { currentPage == PermissionPage }

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
                    PermissionPage -> when (permission) {
                        OnboardingPermission.Microphone -> OnboardingPermissionPage(
                            icon = Icons.Outlined.Mic,
                            title = "Dê voz à Olivia",
                            description = "Use o microfone do celular para iniciar e controlar o take por voz. O áudio do take não alimenta a IA.",
                        )
                        OnboardingPermission.Bluetooth -> OnboardingPermissionPage(
                            icon = Icons.Outlined.Bluetooth,
                            title = "Conecte seus óculos",
                            description = "O celular faz a ponte com os Ray-Ban Meta durante o take, com autorização do app Meta AI.",
                        )
                        null -> OnboardingReady()
                    }
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
            OliviaButton(
                onClick = {
                    when {
                        currentPage < PermissionPage -> page = currentPage + 1
                        permission == OnboardingPermission.Microphone -> onRequestAudioPermission()
                        permission == OnboardingPermission.Bluetooth -> onRequestBluetoothPermission()
                        else -> onEnter()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
                variant = OliviaButtonVariant.PrimaryGradient,
            ) {
                Text(
                    text = onboardingButtonLabel(
                        page = currentPage,
                        permission = permission,
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
        OnboardingVisualCard()
        OnboardingHeading(
            title = "Grave com as mãos livres",
            description = "A Olivia observa o enquadramento, a luz e o horizonte, e orienta você por áudio.",
        )
    }
}

@Composable
private fun OnboardingHowItWorks() {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        OnboardingVisualCard(icon = Icons.Outlined.Videocam)
        OnboardingHeading(
            title = "Como funciona",
            description = "O celular recebe os frames dos óculos, analisa um a cada dois segundos e orienta você por áudio. Os frames são descartados depois da análise.",
        )
    }
}

@Composable
private fun OnboardingPermissionPage(
    icon: ImageVector,
    title: String,
    description: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        OnboardingVisualCard(icon = icon)
        OnboardingHeading(
            title = title,
            description = description,
        )
    }
}

@Composable
private fun OnboardingReady() {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        OnboardingVisualCard(icon = Icons.Outlined.Check)
        OnboardingHeading(
            title = "Tudo pronto para gravar",
            description = "A Olivia está preparada para acompanhar seu próximo take sem interromper o trabalho.",
        )
    }
}

@Composable
private fun OnboardingVisualCard(icon: ImageVector? = null) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 260.dp, max = 320.dp),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shape = RoundedCornerShape(28.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (icon == null) {
                Image(
                    painter = painterResource(R.drawable.olivia_logo),
                    contentDescription = "Olivia",
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(236.dp)
                        .clip(RoundedCornerShape(24.dp)),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(128.dp)
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(40.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
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
    permission: OnboardingPermission?,
): String = when {
    page == IntroPage -> "Começar"
    page == HowItWorksPage -> "Continuar"
    permission == OnboardingPermission.Microphone -> "Permitir microfone"
    permission == OnboardingPermission.Bluetooth -> "Permitir Bluetooth"
    else -> "Entrar na Olivia"
}
