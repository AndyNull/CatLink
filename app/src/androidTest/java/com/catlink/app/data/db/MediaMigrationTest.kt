package com.catlink.app.data.db

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private var helper: SupportSQLiteOpenHelper? = null

    @After
    fun tearDown() {
        helper?.close()
    }

    @Test
    fun migration15To16PreservesLocalAndRemoteAlbumRows() {
        helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name("media-migration-${System.nanoTime()}.db")
                .callback(object : SupportSQLiteOpenHelper.Callback(15) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE media (id TEXT NOT NULL PRIMARY KEY, catId TEXT NOT NULL, uri TEXT NOT NULL, createdAt INTEGER NOT NULL)")
                        db.execSQL("INSERT INTO media VALUES ('local','cat','/data/user/0/app/files/photos/a.jpg',10)")
                        db.execSQL("INSERT INTO media VALUES ('remote','cat','https://cdn.example/cat/b.jpg',20)")
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )
        val db = helper!!.writableDatabase

        AppDatabase.MIGRATION_15_16.migrate(db)

        val columns = db.query("PRAGMA table_info(media)").use { cursor ->
            buildSet {
                val index = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) add(cursor.getString(index))
            }
        }
        assertTrue(columns.containsAll(setOf("localUri", "remoteUrl", "serverMediaId", "syncState", "sortOrder")))
        db.query("SELECT localUri, remoteUrl, syncState, sortOrder FROM media WHERE id = 'local'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("/data/user/0/app/files/photos/a.jpg", cursor.getString(0))
            assertEquals("", cursor.getString(1))
            assertEquals("local", cursor.getString(2))
            assertEquals(10L, cursor.getLong(3))
        }
        db.query("SELECT localUri, remoteUrl, syncState, sortOrder FROM media WHERE id = 'remote'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("", cursor.getString(0))
            assertEquals("https://cdn.example/cat/b.jpg", cursor.getString(1))
            assertEquals("uploaded", cursor.getString(2))
            assertEquals(20L, cursor.getLong(3))
        }
    }
}
