package com.github.adamantcheese.database.di

import android.app.Application
import com.github.adamantcheese.database.di.annotation.LoggerTagPrefix
import com.github.adamantcheese.database.di.annotation.OkHttpDns
import com.github.adamantcheese.database.di.annotation.OkHttpProtocols
import com.github.adamantcheese.database.di.annotation.VerboseLogs
import com.github.adamantcheese.database.repository.InlinedFileInfoRepository
import com.github.adamantcheese.database.repository.MediaServiceLinkExtraContentRepository
import com.github.adamantcheese.database.repository.SeenPostRepository
import dagger.BindsInstance
import dagger.Component
import okhttp3.Dns
import javax.inject.Singleton

@Singleton
@Component(
        modules = [
            NetworkModule::class,
            DatabaseModule::class
        ]
)
interface DatabaseComponent {
    fun inject(application: Application)

    fun getMediaServiceLinkExtraContentRepository(): MediaServiceLinkExtraContentRepository
    fun getSeenPostRepository(): SeenPostRepository
    fun getInlinedFileInfoRepository(): InlinedFileInfoRepository

    @Component.Builder
    interface Builder {
        @BindsInstance
        fun application(application: Application): Builder
        @BindsInstance
        fun loggerTagPrefix(@LoggerTagPrefix loggerTagPrefix: String): Builder
        @BindsInstance
        fun verboseLogs(@VerboseLogs verboseLogs: Boolean): Builder
        @BindsInstance
        fun okHttpDns(@OkHttpDns dns: Dns): Builder
        @BindsInstance
        fun okHttpProtocols(@OkHttpProtocols okHttpProtocols: NetworkModule.OkHttpProtocolList): Builder

        fun build(): DatabaseComponent
    }

}