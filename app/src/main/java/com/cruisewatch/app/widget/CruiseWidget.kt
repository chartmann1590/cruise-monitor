package com.cruisewatch.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.cruisewatch.app.MainActivity
import com.cruisewatch.app.data.Alert
import com.cruisewatch.app.data.CruiseRepository
import com.cruisewatch.app.data.TrackedCruise
import com.google.firebase.auth.FirebaseAuth
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val OceanDeep = Color(0xFF012A4A)
private val Teal = Color(0xFF00B4A6)
private val Coral = Color(0xFFFF6B57)
private val Gold = Color(0xFFFFC94D)
private val White = Color.White

class CruiseWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val auth = FirebaseAuth.getInstance()
        val signedIn = auth.currentUser != null

        val (cruises, alerts) = if (signedIn) {
            val repo = CruiseRepository()
            repo.trackedCruisesOnce() to repo.alertsOnce()
        } else {
            emptyList<TrackedCruise>() to emptyList<Alert>()
        }

        provideContent {
            GlanceTheme {
                WidgetContent(signedIn = signedIn, cruises = cruises, alerts = alerts)
            }
        }
    }
}

@Composable
private fun WidgetContent(signedIn: Boolean, cruises: List<TrackedCruise>, alerts: List<Alert>) {
    val context = LocalContext.current
    val openApp = actionStartActivity(Intent(context, MainActivity::class.java))

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(OceanDeep))
            .cornerRadius(20.dp)
            .clickable(openApp)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
            Text(
                "⛴ CruiseWatch",
                style = TextStyle(color = ColorProvider(White), fontWeight = FontWeight.Bold, fontSize = 13.sp),
            )
        }
        Spacer(modifier = GlanceModifier.height(10.dp))

        val unclaimed = alerts.firstOrNull { !it.claimed }
        val nearestCruise = cruises.minByOrNull {
            runCatching { LocalDate.parse(it.finalPaymentDate, DateTimeFormatter.ISO_LOCAL_DATE) }
                .getOrDefault(LocalDate.MAX)
        }

        when {
            !signedIn -> WidgetMessage("Sign in to see your cruises")
            unclaimed != null -> {
                Text(
                    "🎉 Price dropped $${"%.2f".format(unclaimed.dropAmount)}",
                    style = TextStyle(color = ColorProvider(Coral), fontWeight = FontWeight.Bold, fontSize = 16.sp),
                )
                Spacer(modifier = GlanceModifier.height(4.dp))
                Text(
                    "New fare $${"%.2f".format(unclaimed.currentFare)} · tap to claim",
                    style = TextStyle(color = ColorProvider(White), fontSize = 12.sp),
                )
            }
            nearestCruise != null -> {
                val daysLeft = runCatching {
                    LocalDate.now().until(LocalDate.parse(nearestCruise.finalPaymentDate, DateTimeFormatter.ISO_LOCAL_DATE)).days
                }.getOrNull()
                Text(
                    nearestCruise.ship,
                    style = TextStyle(color = ColorProvider(White), fontWeight = FontWeight.Bold, fontSize = 15.sp),
                )
                Spacer(modifier = GlanceModifier.height(4.dp))
                Text(
                    "You paid ${nearestCruise.currency} ${"%.2f".format(nearestCruise.farePaid)}",
                    style = TextStyle(color = ColorProvider(Teal), fontSize = 12.sp),
                )
                if (daysLeft != null && daysLeft >= 0) {
                    Spacer(modifier = GlanceModifier.height(2.dp))
                    Text(
                        "$daysLeft day${if (daysLeft == 1) "" else "s"} to final payment",
                        style = TextStyle(color = ColorProvider(Gold), fontSize = 12.sp),
                    )
                }
            }
            else -> WidgetMessage("No cruises tracked yet — tap to add one")
        }
    }
}

@Composable
private fun WidgetMessage(text: String) {
    Text(
        text,
        style = TextStyle(color = ColorProvider(White), fontSize = 13.sp),
        modifier = GlanceModifier.fillMaxWidth(),
    )
}

class CruiseWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CruiseWidget()
}
