package com.innovify.skillswap.viewmodel

// Validación local del formulario de registro (el backend vuelve a validar).
object SignUpValidator {

    const val MIN_PASSWORD = 8

    // Devuelve el mensaje del primer problema, o null si todo está bien.
    fun validate(username: String, email: String, password: String, confirm: String): String? {
        val user = username.trim()
        val mail = email.trim().lowercase()
        return when {
            user.isEmpty() -> "Escribe un nombre de usuario"
            user.contains(" ") -> "El usuario no puede tener espacios"
            !isInstitutionalEmail(mail) -> "Usa tu correo institucional (termina en .edu.pe)"
            password.length < MIN_PASSWORD -> "La contraseña debe tener al menos $MIN_PASSWORD caracteres"
            password != confirm -> "Las contraseñas no coinciden"
            else -> null
        }
    }

    fun isInstitutionalEmail(email: String): Boolean {
        return Regex("^[^@\\s]+@[^@\\s]+\\.edu\\.pe$").matches(email.trim().lowercase())
    }
}
