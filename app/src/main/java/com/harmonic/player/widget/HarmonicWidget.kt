package com.harmonic.player.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.action.ActionParameters
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.harmonic.player.MainActivity
import com.harmonic.player.R
import com.harmonic.player.playback.PlaybackServiceHolder

/**
 * Widget de tela inicial: mostra a música atual e os controles básicos
 * (anterior, play/pause, próxima). Toque no título/artista abre o app na
 * tela "Agora Tocando".
 *
 * O estado vem do [PlaybackServiceHolder] — como o widget roda no mesmo
 * processo do app, ele lê direto o valor mais recente sempre que
 * `updateAll`/`update` é chamado pelo PlaybackService, sem IPC.
 *
 * Deliberadamente simples nesta primeira versão (texto + emoji nos botões,
 * em vez de ícones vetoriais) — a API de widgets do Glance é bem mais
 * limitada que o Compose "normal" (não aceita Icons.Filled, por exemplo),
 * então preferimos o caminho mais seguro a arriscar APIs que talvez nem
 * existam na versão do Glance que estamos usando.
 */
class HarmonicWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            WidgetContent()
        }
    }

    @Composable
    private fun WidgetContent() {
        val state = PlaybackServiceHolder.state.value
        val white = ColorProvider(Color.White)
        val gray = ColorProvider(Color(0xFFE0E0E0))

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                // Gradiente: 100% transparente no topo (o papel de parede do
                // sistema aparece direto atrás do título) e escurece aos
                // poucos até a base, só o suficiente pra dar contraste aos
                // botões — em vez do retângulo escuro sólido de antes.
                .background(ImageProvider(R.drawable.widget_bg_gradient))
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Text(
                text = state.title ?: "Harmonic",
                style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp, color = white),
                maxLines = 1,
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .clickable(actionStartActivity<MainActivity>())
            )
            Text(
                text = state.artist ?: if (state.hasQueue) "" else "Nenhuma música tocando",
                style = TextStyle(fontSize = 13.sp, color = gray),
                maxLines = 1,
                modifier = GlanceModifier.fillMaxWidth()
            )

            Spacer(modifier = GlanceModifier.height(14.dp))

            // Botões "físicos": cada um tem seu próprio drawable em camadas
            // (sombra + corpo em gradiente + reflexo) que simula um botão
            // saltando da superfície do widget, e "afunda" visualmente
            // enquanto pressionado (ver widget_button_3d_selector.xml).
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PhysicalButton(
                    emoji = "⏮",
                    size = 46.dp,
                    fontSize = 18.sp,
                    background = R.drawable.widget_button_3d_selector,
                    onClick = actionRunCallback<PreviousAction>()
                )
                Spacer(modifier = GlanceModifier.width(18.dp))
                PhysicalButton(
                    emoji = if (state.isPlaying) "⏸" else "▶",
                    size = 60.dp,
                    fontSize = 22.sp,
                    background = R.drawable.widget_button_3d_primary_selector,
                    onClick = actionRunCallback<PlayPauseAction>()
                )
                Spacer(modifier = GlanceModifier.width(18.dp))
                PhysicalButton(
                    emoji = "⏭",
                    size = 46.dp,
                    fontSize = 18.sp,
                    background = R.drawable.widget_button_3d_selector,
                    onClick = actionRunCallback<NextAction>()
                )
            }
        }
    }

    /** Botão circular "saltado" da superfície — capa em drawable simula o 3D, aqui só centralizamos o ícone. */
    @Composable
    private fun PhysicalButton(
        emoji: String,
        size: androidx.compose.ui.unit.Dp,
        fontSize: androidx.compose.ui.unit.TextUnit,
        background: Int,
        onClick: androidx.glance.action.Action
    ) {
        Box(
            modifier = GlanceModifier
                .size(size)
                .background(ImageProvider(background))
                .clickable(onClick),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = emoji,
                style = TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = fontSize,
                    color = ColorProvider(Color.White),
                    textAlign = TextAlign.Center
                )
            )
        }
    }
}

class PlayPauseAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        PlaybackServiceHolder.togglePlayPause()
        PlaybackServiceHolder.refreshState()
        HarmonicWidget().update(context, glanceId)
    }
}

class NextAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        PlaybackServiceHolder.skipNext()
        PlaybackServiceHolder.refreshState()
        HarmonicWidget().update(context, glanceId)
    }
}

class PreviousAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        PlaybackServiceHolder.skipPrevious()
        PlaybackServiceHolder.refreshState()
        HarmonicWidget().update(context, glanceId)
    }
}

/** Receiver que registra o widget no sistema — obrigatório pro Android reconhecer o Glance widget. */
class HarmonicWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HarmonicWidget()
}
