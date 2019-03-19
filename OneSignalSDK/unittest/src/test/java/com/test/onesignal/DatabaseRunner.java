package com.test.onesignal;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.onesignal.BuildConfig;
import com.onesignal.OneSignalDbHelper;
import com.onesignal.ShadowOneSignalDbHelper;
import com.onesignal.StaticResetHelper;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLog;

import com.onesignal.OneSignalPackagePrivateHelper.NotificationTable;

import static junit.framework.Assert.assertEquals;

@Config(
   packageName = "com.onesignal.example",
   constants = BuildConfig.class,
   instrumentedPackages = { "com.onesignal" },
   shadows = { ShadowOneSignalDbHelper.class },
   sdk = 26
)
@RunWith(RobolectricTestRunner.class)
public class DatabaseRunner {
   @BeforeClass // Runs only once, before any tests
   public static void setUpClass() {
      ShadowLog.stream = System.out;
      TestHelpers.beforeTestSuite();
      StaticResetHelper.saveStaticValues();
   }

   @Before // Before each test
   public void beforeEachTest() {
      TestHelpers.beforeTestInitAndCleanup();
   }

   @AfterClass
   public static void afterEverything() {
      StaticResetHelper.restSetStaticFields();
   }

   @Test
   public void shouldUpgradeDbFromV2ToV3() throws Exception {
      // 1. Init DB as version 2 and add one notification record
      ShadowOneSignalDbHelper.DATABASE_VERSION = 2;
      SQLiteDatabase writableDatabase = OneSignalDbHelper.getInstance(RuntimeEnvironment.application).getWritableDatabase();
      writableDatabase.beginTransaction();
      ContentValues values = new ContentValues();
      values.put(NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID, 1);
      writableDatabase.insertOrThrow(NotificationTable.TABLE_NAME, null, values);
      writableDatabase.setTransactionSuccessful();
      writableDatabase.endTransaction();
      writableDatabase.close();

      // 2. Clear the cache of the DB so it reloads the file.
      ShadowOneSignalDbHelper.restSetStaticFields();
      ShadowOneSignalDbHelper.igngoreDuplicatedFieldsOnUpgrade = true;


      // 3. Opening the DB will auto trigger the update.
      SQLiteDatabase readableDatabase = OneSignalDbHelper.getInstance(RuntimeEnvironment.application).getReadableDatabase();

      // 4. Check to make sure the new expire_time value is the created_at time + 72 hours as a default
      String[] selectColumns = {
         NotificationTable.COLUMN_NAME_CREATED_TIME,
         NotificationTable.COLUMN_NAME_EXPIRE_TIME
      };
      Cursor cursor = readableDatabase.query(
         NotificationTable.TABLE_NAME,
         selectColumns,
         null,
         null,
         null, // group by
         null, // filter by row groups
         null, // sort order, new to old
         null // limit
      );
      cursor.moveToFirst();

      long createdTime = Long.parseLong(cursor.getString(cursor.getColumnIndex(NotificationTable.COLUMN_NAME_CREATED_TIME)));
      long expireTime = Long.parseLong(cursor.getString(cursor.getColumnIndex(NotificationTable.COLUMN_NAME_EXPIRE_TIME)));
      assertEquals(createdTime + (72L * (60 * 60)), expireTime);
   }
}