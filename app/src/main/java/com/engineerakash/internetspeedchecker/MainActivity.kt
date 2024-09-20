package com.engineerakash.internetspeedchecker


import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.engineerakash.internetspeedchecker.databinding.ActivityMainBinding
import com.engineerakash.internetspeedchecker.util.DOWNLOAD_ENDPOINT
import com.engineerakash.internetspeedchecker.util.FileUtil
import com.engineerakash.internetspeedchecker.util.NetworkUtil
import com.engineerakash.internetspeedchecker.util.UPLOAD_ENDPOINT
import com.engineerakash.internetspeedchecker.util.UPLOAD_FILE_SIZE_IN_MB
import com.engineerakash.internetspeedchecker.util.uptoFixDecimalIfDecimalValueIsZero
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.system.measureTimeMillis

private const val TAG = "akt"

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private var isDownloadTestInProgress = false
    private var isUploadTestInProgress = false

    private var isTestInProgress = isDownloadTestInProgress || isUploadTestInProgress

    private var uploadJob: Job? = null
    private var downloadJob: Job? = null

    private var uploadSpeedFlow: MutableSharedFlow<Double> = MutableSharedFlow<Double>()

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

        setupListenersAndObservers()

        if (NetworkUtil.isInternetConnected(this)) {
            startTest()
        } else {
            showErrorCase(getString(R.string.internet_not_connected))
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun setupListenersAndObservers() {
        binding.retryIv.setOnClickListener {
            binding.errorTv.visibility = View.GONE

            if (isTestInProgress) {
                pauseTest()
            } else {
                startTest()
            }
        }

        uploadSpeedFlow
            .onEach {
                Log.d(TAG, "setupListenersAndObservers: uploadSpeedFlow $it")

                binding.uploadSpeedTv.text = if (it > (1024 * 1024)) {
                    "${(it / (1024 * 1024)).uptoFixDecimalIfDecimalValueIsZero()} Gbps"
                } else if (it > 1024) {
                    "${(it / 1024).uptoFixDecimalIfDecimalValueIsZero()} Mbps"
                } else {
                    "${it.uptoFixDecimalIfDecimalValueIsZero()} Kbps"
                }
            }
            .launchIn(lifecycleScope)
    }

    private fun startTest() {

        binding.retryIv.setImageResource(R.drawable.ic_pause_circle_outline)
        binding.loader.visibility = View.VISIBLE

        testDownloadSpeed(DOWNLOAD_ENDPOINT) { speed ->
            isDownloadTestInProgress = true

            binding.downloadSpeedTv.text = if (speed > (1024 * 1024)) {
                "${(speed / (1024 * 1024)).uptoFixDecimalIfDecimalValueIsZero()} Gbps"
            } else if (speed > 1024) {
                "${(speed / 1024).uptoFixDecimalIfDecimalValueIsZero()} Mbps"
            } else {
                "${speed.uptoFixDecimalIfDecimalValueIsZero()} Kbps"
            }
        }

        uploadJob = lifecycleScope.launch((Dispatchers.IO)) {
            isUploadTestInProgress = true

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
        isDownloadTestInProgress = false
        isUploadTestInProgress = false

        binding.retryIv.setImageResource(R.drawable.ic_refresh)
        binding.loader.visibility = View.GONE
        uploadJob?.cancel()
        downloadJob?.cancel()
    }

    // Function to test download speed
    private fun testDownloadSpeed(url: String, onSpeedCalculated: (Double) -> Unit) {
        // Using Coroutines to perform the download on a background thread
        downloadJob = lifecycleScope.launch(Dispatchers.IO) {
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

                    isDownloadTestInProgress = false
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
        uploadUrl: String, file: File, onSpeedCalculated: (Double) -> Unit
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
            var totalBytesUploaded = 0L

            val uploadTime = measureTimeMillis {
                FileInputStream(file).use { input ->
                    connection.outputStream.use { output ->
                        var bytesRead: Int
                        val uploadStartTime = System.currentTimeMillis()

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            if (uploadJob?.isActive != true) {
                                //this job has been cancelled
                                return
                            }

                            output.write(buffer, 0, bytesRead)

                            totalBytesUploaded += bytesRead

                            // Calculate upload speed in Kbps (Bytes to Kbps conversion)
                            val timeElapsedSinceStart = System.currentTimeMillis() - uploadStartTime
                            val currentSpeedKbps =
                                (totalBytesUploaded / 1024.0) / (timeElapsedSinceStart / 1000.0)

                            Log.d(TAG, "testUploadSpeed: totalBytesUploaded $totalBytesUploaded")
                            Log.d(
                                TAG,
                                "testUploadSpeed: timeElapsedSinceStart $timeElapsedSinceStart"
                            )
                            Log.d(TAG, "testUploadSpeed: chunkSpeedKbps $currentSpeedKbps")

                            uploadSpeedFlow.emit(currentSpeedKbps)
                        }
                    }
                }
            }

            // Calculate upload speed (in Kbps)
            val uploadSpeedKbps = (fileLength / 1024) / (uploadTime / 1000.0)

            // Notify the upload speed on the main thread
            withContext(Dispatchers.Main) {
                onSpeedCalculated(uploadSpeedKbps)

                isUploadTestInProgress = false
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
        isDownloadTestInProgress = false
        isUploadTestInProgress = false

        binding.retryIv.setImageResource(R.drawable.ic_refresh)

        binding.loader.visibility = View.GONE
    }
}