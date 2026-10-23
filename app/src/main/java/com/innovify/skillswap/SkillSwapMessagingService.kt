package com.innovify.skillswap

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.google.gson.Gson
import com.innovify.skillswap.model.beans.AuthUser
import com.innovify.skillswap.model.beans.DeviceTokenRequest
import com.innovify.skillswap.model.client.RetrofitClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

const val NOTIFICATION_CHANNEL_ID = "skillswap_updates"

// Canal de las notificaciones (Android 8+). Se crea al abrir la app y al recibir un mensaje.
fun createNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val channel = NotificationChannel(
        NOTIFICATION_CHANNEL_ID, "Avisos de SkillSwap", NotificationManager.IMPORTANCE_HIGH
    ).apply { description = "Resultados de tus entregas y de la revisión de tus casos" }
    context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
}

// Push de Firebase. Con la app en segundo plano el sistema muestra la notificación; aquí se atienden
// el token nuevo y los mensajes que llegan con la app abierta.
class SkillSwapMessagingService : FirebaseMessagingService() {

    // El servicio no tiene ViewModel: usa el JWT guardado en la sesión. Sin sesión o sin permiso, no hace nada.
    override fun onNewToken(token: String) {
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return
        val raw = getSharedPreferences("skillswap_session", Context.MODE_PRIVATE).getString("user", null) ?: return
        val saved = try {
            Gson().fromJson(raw, AuthUser::class.java)
        } catch (e: Exception) {
            null
        } ?: return
        if (saved.token.isEmpty()) return
        RetrofitClient.token = saved.token
        CoroutineScope(Dispatchers.IO).launch {
            try {
                RetrofitClient.webService.registerDeviceToken(DeviceTokenRequest(token))
            } catch (e: Exception) {
                // sin red: se reenvía al abrir la app
            }
        }
    }

    // Solo se llama con la app en primer plano: la notificación hay que crearla aquí.
    override fun onMessageReceived(message: RemoteMessage) {
        val notification = message.notification ?: return
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return

        createNotificationChannel(this)
        // Los datos (type, caseId…) viajan como extras para que MainActivity abra la pantalla correcta
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            message.data.forEach { (key, value) -> putExtra(key, value) }
        }
        val id = System.currentTimeMillis().toInt()
        val pending = PendingIntent.getActivity(
            this, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val built = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(notification.title)
            .setContentText(notification.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notification.body))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        NotificationManagerCompat.from(this).notify(id, built)
    }
}
