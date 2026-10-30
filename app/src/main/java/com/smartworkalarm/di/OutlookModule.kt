package com.smartworkalarm.di

import android.content.Context
import androidx.room.Room
import com.smartworkalarm.BuildConfig
import com.smartworkalarm.outlook.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.time.Clock
import java.time.ZoneId
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object OutlookModule {
    @Provides @Singleton fun configuration(@ApplicationContext context: Context) =
        OutlookConfiguration(BuildConfig.OUTLOOK_CLIENT_ID, BuildConfig.OUTLOOK_SIGNATURE_HASH, context.packageName)

    @Provides @Singleton fun database(@ApplicationContext context: Context): OutlookDatabase =
        Room.databaseBuilder(context, OutlookDatabase::class.java, File(context.noBackupFilesDir, "outlook-cache.db").absolutePath).build()

    @Provides @Singleton fun store(database: OutlookDatabase): OutlookStore = RoomOutlookStore(database)
    @Provides @Singleton fun auth(@ApplicationContext context: Context, configuration: OutlookConfiguration): OutlookAuth =
        MsalOutlookAuth(context, configuration)
    @Provides @Singleton fun graph(clock: Clock): OutlookGraphGateway = OutlookGraphClient(HttpsGraphTransport(), clock)
    @Provides @Singleton fun sync(store: OutlookStore, auth: OutlookAuth, graph: OutlookGraphGateway, clock: Clock) =
        OutlookSyncCoordinator(store, auth, graph, clock, ZoneId::systemDefault)
    @Provides @Singleton fun scheduler(@ApplicationContext context: Context, store: OutlookStore, clock: Clock): OutlookSyncScheduler =
        WorkManagerOutlookScheduler(context, store, clock)
    @Provides @Singleton fun controller(store: OutlookStore, auth: OutlookAuth, sync: OutlookSyncCoordinator,
        scheduler: OutlookSyncScheduler, alarms: OutlookAlarmReconciler) =
        OutlookController(store, auth, sync, scheduler, alarms::execute)
    @Provides @Singleton fun runtime(store: OutlookStore, scheduler: OutlookSyncScheduler) = OutlookRuntime(store, scheduler)
}
