package dev.otherworld.shoppinglist.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.otherworld.shoppinglist.domain.text.SmartInput
import java.util.Locale
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideSmartInput(): SmartInput =
        SmartInput.forLanguage(Locale.getDefault().language)
}
