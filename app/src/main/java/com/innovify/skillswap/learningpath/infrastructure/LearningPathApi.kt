package com.innovify.skillswap.learningpath.infrastructure

import com.innovify.skillswap.learningpath.domain.DeclareGoalRequest
import com.innovify.skillswap.learningpath.domain.LearningPath
import com.innovify.skillswap.learningpath.domain.SkillOption
import com.innovify.skillswap.learningpath.domain.SkillOptionsRequest
import com.innovify.skillswap.shared.infrastructure.network.RetrofitClient
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface LearningPathApi {

    // Todas las rutas del Estudiante, la más reciente primero. Solo el dueño puede leerlas.
    @GET("api/v1/learning-paths")
    suspend fun getLearningPaths(@Query("studentId") studentId: Int): Response<List<LearningPath>>

    // T46/T47: habilidades candidatas del catálogo para la meta, de mayor a menor afinidad.
    // 200 lista | 400 meta vacía o muy larga | 422 la meta no coincide con ninguna habilidad
    @POST("api/v1/learning-paths/skill-options")
    suspend fun getSkillOptions(@Body body: SkillOptionsRequest): Response<List<SkillOption>>

    // Declara la meta y construye la ruta con la habilidad confirmada.
    // 201 LearningPath | 400 | 409 límite del plan o ya demostró todas las habilidades | 422 meta sin habilidades
    @POST("api/v1/learning-paths")
    suspend fun declareGoal(@Body body: DeclareGoalRequest): Response<LearningPath>

    // Pausa una ruta activa: conserva su progreso pero no se puede avanzar. 200 LearningPath | 403 | 404 | 409 no está activa
    @PATCH("api/v1/learning-paths/{pathId}/pause")
    suspend fun pausePath(@Path("pathId") pathId: Int): Response<LearningPath>

    // Reanuda una ruta pausada si el plan admite otra activa. 200 LearningPath | 403 | 404 | 409 no está pausada o PlanLimitReached
    @PATCH("api/v1/learning-paths/{pathId}/resume")
    suspend fun resumePath(@Path("pathId") pathId: Int): Response<LearningPath>

}

val learningPathApi: LearningPathApi by lazy { RetrofitClient.create(LearningPathApi::class.java) }
