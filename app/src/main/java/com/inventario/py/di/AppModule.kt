package com.inventario.py.di

import android.content.Context
import androidx.work.WorkManager
import com.inventario.py.data.local.dao.*
import com.inventario.py.data.local.database.InventarioDatabase
import com.inventario.py.data.remote.api.InventarioApi
import com.inventario.py.utils.ExcelExporter
import com.inventario.py.utils.RestoreManager
import com.inventario.py.worker.BackupManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "inventario_prefs")

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // ========== Database ==========

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): InventarioDatabase {
        return InventarioDatabase.getInstance(context)
    }

    @Provides
    fun provideProductDao(database: InventarioDatabase): ProductDao {
        return database.productDao()
    }

    @Provides
    fun provideSaleDao(database: InventarioDatabase): SaleDao {
        return database.saleDao()
    }

    @Provides
    fun provideUserDao(database: InventarioDatabase): UserDao {
        return database.userDao()
    }

    @Provides
    fun provideCartDao(database: InventarioDatabase): CartDao {
        return database.cartDao()
    }

    @Provides
    fun provideProductHistoryDao(database: InventarioDatabase): ProductHistoryDao {
        return database.productHistoryDao()
    }

    // ========== Network ==========

    @Provides
    @Singleton
    fun provideOkHttpClient(@ApplicationContext context: Context): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val authInterceptor = Interceptor { chain ->
            val token = runBlocking {
                try {
                    context.dataStore.data.first()[stringPreferencesKey("auth_token")]
                } catch (e: Exception) {
                    null
                }
            }

            val request = chain.request().newBuilder().apply {
                token?.let { addHeader("Authorization", "Bearer $it") }
                addHeader("Content-Type", "application/json")
                addHeader("Accept", "application/json")
            }.build()

            chain.proceed(request)
        }

        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor(authInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, @ApplicationContext context: Context): Retrofit {
        val baseUrl = runBlocking {
            try {
                context.dataStore.data.first()[stringPreferencesKey("server_url")]
                    ?: "http://localhost:3000/api/"
            } catch (e: Exception) {
                "http://localhost:3000/api/"
            }
        }

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideInventarioApi(retrofit: Retrofit): InventarioApi {
        return retrofit.create(InventarioApi::class.java)
    }

    // ========== Utilities ==========

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager {
        return WorkManager.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideExcelExporter(@ApplicationContext context: Context): ExcelExporter {
        return ExcelExporter(context)
    }

    @Provides
    @Singleton
    fun provideBackupManager(@ApplicationContext context: Context): BackupManager {
        return BackupManager(context)
    }

    @Provides
    @Singleton
    fun provideRestoreManager(
        @ApplicationContext context: Context,
        database: InventarioDatabase
    ): RestoreManager {
        return RestoreManager(context, database)
    }

    // ========== DataStore ==========

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        return context.dataStore
    }
}
