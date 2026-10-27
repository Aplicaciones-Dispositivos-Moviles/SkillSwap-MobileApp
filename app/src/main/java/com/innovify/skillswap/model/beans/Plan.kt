package com.innovify.skillswap.model.beans

// US05 · Plan del Estudiante (StudentPlanResource del backend): Free o Premium, sus límites y la suscripción vigente.
data class ReviewDeadline(
    var amount: Int,
    var unit: String // Hours | BusinessDays
)

data class PlanLimits(
    var maxActiveRoutes: Int,
    var maxTotalRoutes: Int?, // null = sin límite
    var monthlyEscalations: Int,
    var reviewDeadline: ReviewDeadline?
)

data class PlanSubscription(
    var id: Int,
    var studentId: Int,
    var planName: String?,
    var productId: String?,
    var price: Double?,
    var currency: String?,
    var status: String, // Active | Cancelled | Expired
    var currentPeriodEnd: String?,
    var cancelledAt: String?
)

data class StudentPlan(
    var plan: String, // Free | Premium
    var limits: PlanLimits,
    var subscription: PlanSubscription?
)

fun StudentPlan.isPremium(): Boolean = plan == "Premium"

// Premium cancelado: el plan sigue hasta el fin del periodo ya pagado
fun PlanSubscription.isCancelled(): Boolean = status == "Cancelled"

fun PlanSubscription.canCancel(): Boolean = status == "Active"

fun PlanLimits.totalRoutesLabel(): String = maxTotalRoutes?.toString() ?: "Sin límite"

fun ReviewDeadline.label(): String = when (unit) {
    "Hours" -> if (amount == 1) "1 hora" else "$amount horas"
    "BusinessDays" -> if (amount == 1) "1 día hábil" else "$amount días hábiles"
    else -> "$amount $unit"
}
