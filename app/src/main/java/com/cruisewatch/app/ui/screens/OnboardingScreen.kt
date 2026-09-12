package com.cruisewatch.app.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Sailing
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cruisewatch.app.R
import com.cruisewatch.app.i18n.ProvideTranslations
import com.cruisewatch.app.i18n.TranslationManager
import com.cruisewatch.app.i18n.tr
import com.cruisewatch.app.ui.GlassPanel
import com.cruisewatch.app.ui.PhotoHero
import kotlinx.coroutines.launch

private data class OnboardingPage(
    @DrawableRes val photoRes: Int,
    val icon: ImageVector,
    val titleRes: Int,
    val bodyRes: Int,
)

private val pages = listOf(
    OnboardingPage(
        photoRes = R.drawable.hero_cruises,
        icon = Icons.Filled.Sailing,
        titleRes = R.string.onboarding_page1_title,
        bodyRes = R.string.onboarding_page1_body,
    ),
    OnboardingPage(
        photoRes = R.drawable.hero_celebrate,
        icon = Icons.Filled.NotificationsActive,
        titleRes = R.string.onboarding_page2_title,
        bodyRes = R.string.onboarding_page2_body,
    ),
    OnboardingPage(
        photoRes = R.drawable.hero_policies,
        icon = Icons.Filled.CardGiftcard,
        titleRes = R.string.onboarding_page3_title,
        bodyRes = R.string.onboarding_page3_body,
    ),
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    translationManager: TranslationManager,
    onFinish: () -> Unit,
) {
    var languageChosen by remember { mutableStateOf(false) }

    if (!languageChosen) {
        LanguagePickerScreen(
            manager = translationManager,
            onLanguageApplied = { languageChosen = true },
            onCancel = { languageChosen = true },
        )
        return
    }

    ProvideTranslations(translationManager) {
        OnboardingPager(onFinish)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OnboardingPager(onFinish: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val isLastPage by remember { derivedStateOf { pagerState.currentPage == pages.lastIndex } }

    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { pageIndex ->
            val page = pages[pageIndex]
            Column(modifier = Modifier.fillMaxSize()) {
                PhotoHero(photoRes = page.photoRes, height = 380.dp, scrimStrength = 0.65f) {
                    Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Bottom) {
                        Icon(page.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                }
                GlassPanel(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    tint = MaterialTheme.colorScheme.surface,
                    tintAlpha = 1f,
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp).padding(top = 36.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            tr(page.titleRes),
                            style = MaterialTheme.typography.headlineMedium,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            tr(page.bodyRes),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onFinish) {
                    Text(tr(R.string.onboarding_skip), color = Color.White)
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
                    pages.indices.forEach { index ->
                        val active = index == pagerState.currentPage
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .size(if (active) 10.dp else 8.dp)
                                .background(
                                    if (active) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant,
                                    CircleShape,
                                ),
                        )
                    }
                }
                Button(
                    onClick = {
                        if (isLastPage) {
                            onFinish()
                        } else {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        }
                    },
                    shape = MaterialTheme.shapes.large,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Text(if (isLastPage) tr(R.string.onboarding_get_started) else tr(R.string.onboarding_next))
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}
