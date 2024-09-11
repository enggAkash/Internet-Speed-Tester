package com.engineerakash.internetspeedchecker.util

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

object FileUtil {

    suspend fun createDummyFile(
        context: Context,
        fileName: String,
        fileSizeInMB: Int
    ): File {

        return suspendCoroutine<File> {

            // Define file size in bytes (5 MB = 5 * 1024 * 1024 bytes)
            val fileSizeInBytes = fileSizeInMB * 1024 * 1024

            // Create a file in the app's internal storage directory
            val file = File(context.filesDir, fileName)

            // Open FileOutputStream to write to the file
            FileOutputStream(file).use { outputStream ->
                // Define the content to be written (repeated 'A's)
                val content = "A".repeat(1024)  // 1 KB of 'A's

                var bytesWritten = 0
                while (bytesWritten < fileSizeInBytes) {
                    outputStream.write(content.toByteArray())
                    bytesWritten += content.length
                }
            }

            it.resume(file)
        }

    }
}