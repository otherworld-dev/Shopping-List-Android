package dev.otherworld.shoppinglist.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.otherworld.shoppinglist.data.guest.GuestIdLookup
import dev.otherworld.shoppinglist.data.guest.GuestIdStore

@Module
@InstallIn(SingletonComponent::class)
abstract class GuestModule {
    @Binds
    abstract fun bindGuestIdLookup(impl: GuestIdStore): GuestIdLookup
}
