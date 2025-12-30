package com.inventario.py.di

import android.content.Context
import androidx.room.Room
import com.inventario.py.BuildConfig
import com.inventario.py.data.local.dao.*
import com.inventario.py.data.local.database.InventarioDatabase
import com.inventario.py.data.remote.api.InventarioApi
import com.inventario.py.utils.AuthInterceptor
import com.inventario.py.utils.SessionManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    
    // ==================== BASE DE DATOS ====================
    
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): InventarioDatabase {
        return Room.databaseBuilder(
            context,
            InventarioDatabase::class.java,
            InventarioDatabase.DATABASE_NAME
        )
            .fallbackToDestructiveMigration()
            .build()
    }
    
    @Provides
    @Singleton
    fun provideUserDao(database: InventarioDatabase): UserDao {
        return database.userDao()
    }
    
    @Provides
    @Singleton
    fun provideProductDao(database: InventarioDatabase): ProductDao {
        return database.productDao()
    }
    
    @Provides
    @Singleton
    fun provideSalesDao(database: InventarioDatabase): SalesDao {
        return database.salesDao()
    }
    
    @Provides
    @Singleton
    fun provideCartDao(database: InventarioDatabase): CartDao {
        return database.cartDao()
    }
    
    @Provides
    @Singleton
    fun provideSyncQueueDao(database: InventarioDatabase): SyncQueueDao {
        return database.syncQueueDao()
    }
    
    // ==================== RED ====================
    
    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor
    ): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
        
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
    
    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
    
    @Provides
    @Singleton
    fun provideInventarioApi(retrofit: Retrofit): InventarioApi {
        return retrofit.create(InventarioApi::class.java)
    }
    
    // ==================== SESSION ====================
    
    @Provides
    @Singleton
    fun provideSessionManager(@ApplicationContext context: Context): SessionManager {
        return SessionManager(context)
    }
    
    @Provides
    @Singleton
    fun provideAuthInterceptor(sessionManager: SessionManager): AuthInterceptor {
        return AuthInterceptor(sessionManager)
    }
}
