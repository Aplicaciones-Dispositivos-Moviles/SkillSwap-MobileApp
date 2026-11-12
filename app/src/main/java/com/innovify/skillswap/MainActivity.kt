package com.innovify.skillswap

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.innovify.skillswap.assessment.application.MiniProjectViewModel
import com.innovify.skillswap.assessment.application.QuizViewModel
import com.innovify.skillswap.assessment.application.VerificationCaseViewModel
import com.innovify.skillswap.credentialverification.application.CertificateViewModel
import com.innovify.skillswap.finaldemonstration.application.FinalDemonstrationViewModel
import com.innovify.skillswap.iam.application.InterestsViewModel
import com.innovify.skillswap.iam.application.SessionViewModel
import com.innovify.skillswap.learningpath.application.LearningPathViewModel
import com.innovify.skillswap.reputation.application.WalletViewModel
import com.innovify.skillswap.shared.application.SessionStore
import com.innovify.skillswap.shared.presentation.navigation.NavSkillSwap
import com.innovify.skillswap.shared.presentation.theme.SkillSwapTheme
import com.innovify.skillswap.subscription.application.SubscriptionViewModel

// FragmentActivity (y no ComponentActivity) porque BiometricPrompt lo exige; Compose funciona igual
class MainActivity : FragmentActivity() {

    // Every ViewModel is activity-scoped: the state survives moving between screens (Node -> Quiz -> QuizResult)
    // and a notification can select what the next screen opens.
    private val session by viewModels<SessionStore>()
    private val subscriptionViewModel by scoped { SubscriptionViewModel(session) }
    private val learningPathViewModel by scoped { LearningPathViewModel(session, subscriptionViewModel) }
    private val verificationCaseViewModel by scoped { VerificationCaseViewModel(session) }
    private val demonstrationViewModel by scoped { FinalDemonstrationViewModel(session, learningPathViewModel) }
    private val sessionViewModel by scoped {
        SessionViewModel(session, verificationCaseViewModel, demonstrationViewModel)
    }
    private val interestsViewModel by scoped { InterestsViewModel(session) }
    private val walletViewModel by scoped { WalletViewModel(session) }
    private val certificateViewModel by scoped { CertificateViewModel(session, learningPathViewModel) }
    private val quizViewModel by scoped { QuizViewModel(session) }
    private val miniProjectViewModel by scoped { MiniProjectViewModel(session, learningPathViewModel) }

    // The factory only runs the first time; after a rotation the existing ViewModel is returned.
    private inline fun <reified VM : ViewModel> scoped(crossinline build: () -> VM) =
        viewModels<VM> {
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = build() as T
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        createNotificationChannel(applicationContext)
        sessionViewModel.restoreSession(applicationContext) // recupera la sesión guardada, si hay
        session.connectivity.start(applicationContext) // T49: observa la conexión mientras la app vive
        // Notificación tocada con la app cerrada. Al girar la pantalla no se vuelve a procesar.
        if (savedInstanceState == null) openFromNotification(intent)

        setContent {
            SkillSwapTheme {
                NavSkillSwap(
                    sessionViewModel = sessionViewModel,
                    interestsViewModel = interestsViewModel,
                    learningPathViewModel = learningPathViewModel,
                    certificateViewModel = certificateViewModel,
                    quizViewModel = quizViewModel,
                    miniProjectViewModel = miniProjectViewModel,
                    verificationCaseViewModel = verificationCaseViewModel,
                    demonstrationViewModel = demonstrationViewModel,
                    subscriptionViewModel = subscriptionViewModel,
                    walletViewModel = walletViewModel,
                    context = this
                )
            }
        }
    }

    // Notificación tocada con la app ya abierta (launchMode singleTop)
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openFromNotification(intent)
    }

    // Los datos del push (type, caseId…) llegan como extras del Intent
    private fun openFromNotification(intent: Intent?) {
        val type = intent?.getStringExtra("type") ?: return
        sessionViewModel.openFromNotification(
            type,
            intent.getStringExtra("caseId")?.toIntOrNull(),
            intent.getStringExtra("demonstrationId")?.toIntOrNull()
        )
    }
}
