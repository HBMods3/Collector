package com.example

import android.app.Application
import android.content.Context
import com.example.data.github.GitHubApiService
import com.example.data.github.GitHubAuthService
import com.example.data.github.GitHubDatabaseClient
import com.example.data.local.SessionStore
import com.example.data.repository.ShopDatabaseRepository
import com.example.data.repository.ShopJsonCodec
import com.example.worker.ShopMaintenanceWorker
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

class AppContainer(context: Context) {
    val codec = ShopJsonCodec()
    val moshi = codec.moshi

    private val okHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        OkHttpClient.Builder()
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(35, TimeUnit.SECONDS)
            .writeTimeout(35, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()
    }

    val gitHubAuthService: GitHubAuthService by lazy {
        Retrofit.Builder()
            .baseUrl("https://github.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GitHubAuthService::class.java)
    }

    val gitHubApiService: GitHubApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.github.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GitHubApiService::class.java)
    }

    val sessionStore = SessionStore(context.applicationContext, moshi)
    val gitHubClient = GitHubDatabaseClient(gitHubApiService)
    val shopRepo = ShopDatabaseRepository(gitHubClient, codec)
}

class SlrNoteBuApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        ShopMaintenanceWorker.schedule(this)
    }
}
