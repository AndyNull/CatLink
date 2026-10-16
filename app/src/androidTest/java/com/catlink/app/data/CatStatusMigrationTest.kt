package com.catlink.app.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.catlink.app.data.db.AppDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CatStatusMigrationTest {
    private lateinit var context: Context
    private lateinit var helper: SupportSQLiteOpenHelper

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase("cat-status-migration.db")
        helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name("cat-status-migration.db")
                .callback(object : SupportSQLiteOpenHelper.Callback(16) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) = Unit
                    override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )
    }

    @After
    fun tearDown() {
        helper.close()
        context.deleteDatabase("cat-status-migration.db")
    }

    @Test
    fun v16_to_v17_keeps_rows_and_maps_archive_reasons() {
        val db = helper.writableDatabase
        db.execSQL("CREATE TABLE cats (id TEXT PRIMARY KEY NOT NULL, status TEXT NOT NULL)")
        db.execSQL("CREATE TABLE public_drafts (id TEXT PRIMARY KEY NOT NULL, statusCode TEXT NOT NULL, catStatus TEXT NOT NULL)")
        listOf("INTAKE", "RECOVERING", "MATCHING", "FOLLOW_UP", "DECEASED", "LOST_CONTACT", "CUSTOM_STAGE")
            .forEachIndexed { index, status -> db.execSQL("INSERT INTO cats (id,status) VALUES (?,?)", arrayOf(index.toString(), status)) }
        db.execSQL("INSERT INTO public_drafts (id,statusCode,catStatus) VALUES ('lost','lost','失联')")
        db.execSQL("INSERT INTO public_drafts (id,statusCode,catStatus) VALUES ('custom','CUSTOM_STAGE','自定义')")

        AppDatabase.MIGRATION_16_17.migrate(db)

        db.query("SELECT COUNT(*) FROM cats").use { cursor -> cursor.moveToFirst(); assertEquals(7, cursor.getInt(0)) }
        db.query("SELECT status, archiveReason FROM cats WHERE id='4'").use { cursor ->
            cursor.moveToFirst(); assertEquals("archived", cursor.getString(0)); assertEquals("deceased", cursor.getString(1))
        }
        db.query("SELECT status, archiveReason FROM cats WHERE id='5'").use { cursor ->
            cursor.moveToFirst(); assertEquals("archived", cursor.getString(0)); assertEquals("lost", cursor.getString(1))
        }
        db.query("SELECT status, legacyStatus FROM cats WHERE id='6'").use { cursor ->
            cursor.moveToFirst(); assertEquals("rescue", cursor.getString(0)); assertEquals("CUSTOM_STAGE", cursor.getString(1))
        }
        db.query("SELECT statusCode, catStatus, archiveReason FROM public_drafts WHERE id='lost'").use { cursor ->
            cursor.moveToFirst(); assertEquals("archived", cursor.getString(0)); assertEquals("已归档", cursor.getString(1)); assertEquals("lost", cursor.getString(2))
        }
        db.query("SELECT statusCode, catStatus, archiveReason FROM public_drafts WHERE id='custom'").use { cursor ->
            cursor.moveToFirst(); assertEquals("rescue", cursor.getString(0)); assertEquals("救助中", cursor.getString(1)); assertEquals("", cursor.getString(2))
        }
    }
}
