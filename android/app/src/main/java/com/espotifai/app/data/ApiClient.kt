package com.espotifai.app.data

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

interface EspotifaiApi {
    @GET("api/library")
    suspend fun library(): Library

    @GET("api/health")
    suspend fun health(): Map<String, Any?>

    @POST("api/rescan")
    suspend fun rescan(): Map<String, Any?>
}

object ApiClient {

    /**
     * @param readTimeoutSegundos tiempo maximo esperando respuesta. El reindexado
     *   (/api/rescan) tarda minutos con bibliotecas grandes, asi que necesita un
     *   valor alto o el cliente corta y da "error de conexion" aunque el servidor
     *   este bien.
     */
    fun buildOkHttp(token: String, readTimeoutSegundos: Long = 15): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(readTimeoutSegundos, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $token")
                    .build()
                chain.proceed(req)
            }
            .build()

    fun build(
        baseUrl: String,
        token: String,
        readTimeoutSegundos: Long = 15
    ): EspotifaiApi =
        Retrofit.Builder()
            .baseUrl(baseUrl.trimEnd('/') + "/")
            .client(buildOkHttp(token, readTimeoutSegundos))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(EspotifaiApi::class.java)
}
