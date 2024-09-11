package com.engineerakash.internetspeedchecker


import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.engineerakash.internetspeedchecker.databinding.ActivityMainBinding
import com.engineerakash.internetspeedchecker.util.DOWNLOAD_ENDPOINT
import com.engineerakash.internetspeedchecker.util.FileUtil
import com.engineerakash.internetspeedchecker.util.NetworkUtil
import com.engineerakash.internetspeedchecker.util.UPLOAD_ENDPOINT
import com.engineerakash.internetspeedchecker.util.UPLOAD_FILE_SIZE_IN_MB
import com.engineerakash.internetspeedchecker.util.uptoFixDecimalIfDecimalValueIsZero
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.system.measureTimeMillis

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private var isTestInProgress = false
    private var uploadJob: Job? = null
    private var downloadJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityMainBinding.inflate(layoutInflater)

        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.retryIv.setOnClickListener {
            binding.errorTv.visibility = View.GONE

            if (isTestInProgress) {
                pauseTest()
            } else {
                startTest()
            }
        }

        if (NetworkUtil.isInternetConnected(this)) {
            startTest()
        } else {
            showErrorCase(getString(R.string.internet_not_connected))
        }
    }

    private fun startTest() {
        isTestInProgress = true
        binding.retryIv.setImageResource(R.drawable.ic_pause_circle_outline)
        binding.loader.visibility = View.VISIBLE

        testDownloadSpeed(DOWNLOAD_ENDPOINT) { speed ->

            binding.downloadSpeedTv.text = if (speed > (1024 * 1024)) {
                "${(speed / (1024 * 1024)).uptoFixDecimalIfDecimalValueIsZero()} Gbps"
            } else if (speed > 1024) {
                "${(speed / 1024).uptoFixDecimalIfDecimalValueIsZero()} Mbps"
            } else {
                "${speed.uptoFixDecimalIfDecimalValueIsZero()} Kbps"
            }
        }

        uploadJob = CoroutineScope(Dispatchers.IO).launch {
            val fileToUpload =
                FileUtil.createDummyFile(this@MainActivity, "testfile.txt", UPLOAD_FILE_SIZE_IN_MB)

            testUploadSpeed(UPLOAD_ENDPOINT, fileToUpload) { speed ->

                binding.uploadSpeedTv.text = if (speed > (1024 * 1024)) {
                    "${(speed / (1024 * 1024)).uptoFixDecimalIfDecimalValueIsZero()} Gbps"
                } else if (speed > 1024) {
                    "${(speed / 1024).uptoFixDecimalIfDecimalValueIsZero()} Mbps"
                } else {
                    "${speed.uptoFixDecimalIfDecimalValueIsZero()} Kbps"
                }
            }
        }
    }

    private fun pauseTest() {
        isTestInProgress = false
        binding.retryIv.setImageResource(R.drawable.ic_refresh)
        binding.loader.visibility = View.GONE
        uploadJob?.cancel()
        downloadJob?.cancel()
    }

    // Function to test download speed
    private fun testDownloadSpeed(url: String, onSpeedCalculated: (Double) -> Unit) {
        // Using Coroutines to perform the download on a background thread
        downloadJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                val urlConnection = URL(url).openConnection()
                val contentLength = urlConnection.contentLength

                var totalBytesRead = 0L

                val downloadTime = measureTimeMillis {
                    urlConnection.getInputStream().use { input ->
                        val buffer = ByteArray(1024)
                        var bytesRead: Int

                        //todo Calculate download speed (in Kbps) here

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            totalBytesRead += bytesRead

                            if (!isActive) {
                                //this job has been cancelled
                                return@launch
                            }
                        }
                    }
                }

                // Calculate download speed (in Kbps)
                val speedKbps = (totalBytesRead / 1024) / (downloadTime / 1000.0)

                // Call the callback with the calculated speed
                withContext(Dispatchers.Main) {
                    onSpeedCalculated(speedKbps)

                    isTestInProgress = false
                    binding.retryIv.setImageResource(R.drawable.ic_refresh)
                    binding.loader.visibility = View.GONE
                }

            } catch (e: Exception) {
                e.printStackTrace()

                withContext(Dispatchers.Main) {
                    showErrorCase(e.message)
                }
            }
        }
    }

    private suspend fun testUploadSpeed(
        uploadUrl: String,
        file: File,
        onSpeedCalculated: (Double) -> Unit
    ) {
        // Use Coroutines to perform upload on a background thread

        try {
            val url = URL(uploadUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.doOutput = true
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/octet-stream")

            val fileLength = file.length()
            val buffer = ByteArray(1024)

            val uploadTime = measureTimeMillis {
                FileInputStream(file).use { input ->
                    connection.outputStream.use { output ->
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)

                            //todo Calculate upload speed (in Kbps) here

                            if (uploadJob?.isActive != true) {
                                //this job has been cancelled
                                return
                            }
                        }
                    }
                }
            }

            // Calculate upload speed (in Kbps)
            val uploadSpeedKbps = (fileLength / 1024) / (uploadTime / 1000.0)

            // Notify the upload speed on the main thread
            withContext(Dispatchers.Main) {
                onSpeedCalculated(uploadSpeedKbps)

                isTestInProgress = false
                binding.retryIv.setImageResource(R.drawable.ic_refresh)
                binding.loader.visibility = View.GONE
            }

        } catch (e: Exception) {
            e.printStackTrace()

            withContext(Dispatchers.Main) {
                showErrorCase(e.message)
            }
        }
    }

    private fun showErrorCase(error: String?) {
        binding.errorTv.text = error ?: getString(R.string.general_error_message)
        binding.errorTv.visibility = View.VISIBLE
        isTestInProgress = false

        binding.retryIv.setImageResource(R.drawable.ic_refresh)

        binding.loader.visibility = View.GONE
    }
}