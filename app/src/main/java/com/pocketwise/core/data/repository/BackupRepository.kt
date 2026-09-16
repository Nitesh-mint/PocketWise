package com.pocketwise.core.data.repository

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File as DriveFile
import com.google.gson.Gson
import com.pocketwise.core.data.local.AppDatabase
import com.pocketwise.core.data.local.BACKUP_DB_VERSION
import com.pocketwise.core.data.local.BackupSnapshot
import com.pocketwise.core.data.local.CategoryDao
import com.pocketwise.core.data.local.ExpenseDao
import com.pocketwise.core.data.local.RecurringExpenseDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import androidx.room.withTransaction

private const val BACKUP_FILE_NAME = "pocketwise_backup.json"

@Singleton
class BackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val expenseDao: ExpenseDao,
    private val categoryDao: CategoryDao,
    private val recurringDao: RecurringExpenseDao
) {
    private val gson = Gson()

    private fun driveService(account: GoogleSignInAccount): Drive {
        val credential = GoogleAccountCredential.usingOAuth2(
            context, listOf(DriveScopes.DRIVE_APPDATA)
        ).also { it.selectedAccount = account.account }
        return Drive.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), credential)
            .setApplicationName("PocketWise")
            .build()
    }

    suspend fun backup(account: GoogleSignInAccount): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val service = driveService(account)
            val snapshot = BackupSnapshot(
                dbVersion = BACKUP_DB_VERSION,
                expenses = expenseDao.getAll(),
                categories = categoryDao.getAll(),
                recurringExpenses = recurringDao.getAll()
            )
            val json = gson.toJson(snapshot)

            // Delete old backup first (appDataFolder has a 100 MB quota — don't accumulate)
            service.files().list()
                .setSpaces("appDataFolder")
                .setQ("name = '$BACKUP_FILE_NAME'")
                .setFields("files(id)")
                .execute()
                .files
                ?.forEach { service.files().delete(it.id).execute() }

            val metadata = DriveFile().apply {
                name = BACKUP_FILE_NAME
                parents = listOf("appDataFolder")
            }
            service.files()
                .create(metadata, ByteArrayContent("application/json", json.toByteArray(Charsets.UTF_8)))
                .setFields("id")
                .execute()
            Unit
        }
    }

    suspend fun restore(account: GoogleSignInAccount): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val service = driveService(account)
            val files = service.files().list()
                .setSpaces("appDataFolder")
                .setQ("name = '$BACKUP_FILE_NAME'")
                .setFields("files(id)")
                .execute()
            val fileId = files.files?.firstOrNull()?.id
                ?: error("No backup found in Google Drive")

            val output = ByteArrayOutputStream()
            service.files().get(fileId).executeMediaAndDownloadTo(output)
            val snapshot = gson.fromJson(output.toString(Charsets.UTF_8.name()), BackupSnapshot::class.java)

            if (snapshot.dbVersion > BACKUP_DB_VERSION) {
                error("Backup is from a newer version of PocketWise. Please update the app first.")
            }

            db.withTransaction {
                expenseDao.deleteAll()
                categoryDao.deleteAll()
                recurringDao.deleteAll()
                expenseDao.insertAll(snapshot.expenses)
                categoryDao.insertAll(snapshot.categories)
                recurringDao.insertAll(snapshot.recurringExpenses)
            }
        }
    }
}
