package dev.otherworld.shoppinglist.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.otherworld.shoppinglist.data.guest.GuestIdLookup
import dev.otherworld.shoppinglist.data.guest.GuestIdStore
import dev.otherworld.shoppinglist.data.guest.GuestName
import dev.otherworld.shoppinglist.data.guest.GuestNameStore
import dev.otherworld.shoppinglist.data.guest.GuestPasswordStore
import dev.otherworld.shoppinglist.data.guest.GuestPasswords

@Module
@InstallIn(SingletonComponent::class)
abstract class GuestModule {
    @Binds
    abstract fun bindGuestIdLookup(impl: GuestIdStore): GuestIdLookup

    @Binds
    abstract fun bindGuestPasswords(impl: GuestPasswordStore): GuestPasswords

    @Binds
    abstract fun bindGuestName(impl: GuestNameStore): GuestName
}
