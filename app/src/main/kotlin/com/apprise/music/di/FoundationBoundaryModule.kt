package com.apprise.music.di

import com.apprise.music.ads.AdMobPlacementManager
import com.apprise.music.ads.AdPlacementBoundary
import com.apprise.music.billing.BillingClientBoundary
import com.apprise.music.billing.PlayBillingClientManager
import com.apprise.music.consent.ConsentManagerBoundary
import com.apprise.music.consent.UmpConsentManager
import com.apprise.music.database.MusicDatabaseBoundary
import com.apprise.music.database.RoomMusicDatabaseBoundary
import com.apprise.music.entitlement.EntitlementProvider
import com.apprise.music.entitlement.VerifiedEntitlementManager
import com.apprise.music.library.LibraryCatalogBoundary
import com.apprise.music.library.RoomLibraryCatalogBoundary
import com.apprise.music.playback.PlaybackBoundaryStateStore
import com.apprise.music.playback.PlaybackControllerBoundary
import com.apprise.music.preferences.AppPreferencesBoundary
import com.apprise.music.preferences.DataStoreAppPreferencesBoundary
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds production M3/M4 component implementations behind clean M1 boundaries.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class FoundationBoundaryModule {

    @Binds
    @Singleton
    abstract fun bindLibraryCatalogBoundary(impl: RoomLibraryCatalogBoundary): LibraryCatalogBoundary

    @Binds
    @Singleton
    abstract fun bindPlaybackControllerBoundary(impl: PlaybackBoundaryStateStore): PlaybackControllerBoundary

    @Binds
    @Singleton
    abstract fun bindMusicDatabaseBoundary(impl: RoomMusicDatabaseBoundary): MusicDatabaseBoundary

    @Binds
    @Singleton
    abstract fun bindAppPreferencesBoundary(impl: DataStoreAppPreferencesBoundary): AppPreferencesBoundary

    @Binds
    @Singleton
    abstract fun bindBillingClientBoundary(impl: PlayBillingClientManager): BillingClientBoundary

    @Binds
    @Singleton
    abstract fun bindAdPlacementBoundary(impl: AdMobPlacementManager): AdPlacementBoundary

    @Binds
    @Singleton
    abstract fun bindEntitlementProvider(impl: VerifiedEntitlementManager): EntitlementProvider

    @Binds
    @Singleton
    abstract fun bindConsentManagerBoundary(impl: UmpConsentManager): ConsentManagerBoundary
}
