package com.olivia.companion.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.olivia.companion.R
import com.olivia.companion.ui.OliviaButton
import com.olivia.companion.ui.OliviaButtonVariant
import com.olivia.companion.ui.OliviaBlueActionOn

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
    val currentPage = page.coerceIn(0, OnboardingPageCount - 1)
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
            AnimatedContent(
                targetState = currentPage,
                label = "onboarding page",
            ) { currentPage ->
                when (currentPage) {
                    IntroPage -> OnboardingIntro()
                    HowItWorksPage -> OnboardingHowItWorks()
                    PermissionPage -> when (permission) {
                        OnboardingPermission.Microphone -> OnboardingPermissionPage(
                            title = "Dê voz à Olivia",
                            description = "Use o microfone do celular para iniciar e controlar o take por voz. O áudio do take não alimenta a IA.",
                        )
                        OnboardingPermission.Bluetooth -> OnboardingPermissionPage(
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
        ) {
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
                    style = MaterialTheme.typography.labelLarge.copy(
                        color = OliviaBlueActionOn,
                        fontWeight = FontWeight.Bold,
                    ),
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
        OnboardingVisualCard(imageRes = R.drawable.olivia_room_3)
        OnboardingHeading(
            title = "Como funciona",
            description = "A Olivia analisa os frames dos óculos e orienta você por áudio.",
        )
    }
}

@Composable
private fun OnboardingPermissionPage(
    title: String,
    description: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        OnboardingVisualCard(imageRes = R.drawable.olivia_cast)
        OnboardingHeading(
            title = title,
            description = description,
        )
    }
}

@Composable
private fun OnboardingReady() {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        OnboardingVisualCard(imageRes = R.drawable.olivia_cast)
        OnboardingHeading(
            title = "Tudo pronto para gravar",
            description = "A Olivia está preparada para acompanhar seu próximo take sem interromper o trabalho.",
        )
    }
}

@Composable
private fun OnboardingVisualCard(imageRes: Int? = null) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 260.dp, max = 320.dp),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shape = RoundedCornerShape(28.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (imageRes != null) {
                Image(
                    painter = painterResource(imageRes),
                    contentDescription = "Olivia",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(28.dp)),
                    contentScale = ContentScale.Crop,
                )
            } else {
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
