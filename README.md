# SkillSwap-MobileApp

Aplicación nativa del **Estudiante** de SkillSwap (Kotlin + Jetpack Compose), organizada por bounded contexts de DDD:
`iam`, `learningpath`, `credentialverification`, `assessment`, `finaldemonstration`, `subscription`, `reputation` y `shared`,
cada uno con sus capas `domain`, `application`, `infrastructure` y `presentation`.

## Compilar desde un clon nuevo

1. **JDK 21** y el **Android SDK** (`ANDROID_HOME` apuntando a él, por ejemplo `~/Android/Sdk`).
2. **`app/google-services.json`**: el plugin de Google Services lo exige y no se versiona (`.gitignore`).
   Descárgalo de la consola de Firebase del proyecto `skillswap-moviles` → Configuración del proyecto →
   app Android `com.innovify.skillswap`, o pídeselo al equipo, y colócalo en `app/`.
3. Pruebas y APK de depuración:

   ```bash
   ./gradlew testDebugUnitTest assembleDebug
   ```

   El APK queda en `app/build/outputs/apk/debug/app-debug.apk`.

La app consume el backend publicado en `https://skillswap-innovify.duckdns.org` (Swagger en `/swagger-ui/index.html`).
