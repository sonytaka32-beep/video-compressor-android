package com.example.videocompressor

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.format.Formatter
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.videocompressor.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var queueAdapter: QueueAdapter

    private val pickVideosLauncher = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri ->
                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: SecurityException) {
                }
            }
            viewModel.addInputFiles(uris)
        }
    }

    private val pickOutputFolderLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { treeUri: Uri? ->
        if (treeUri != null) {
            try {
                contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: SecurityException) {
            }
            viewModel.selectOutputFolder(treeUri)
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        createNotificationChannel()
        requestNotificationPermissionIfNeeded()
        setupPresetDropdown()
        setupRecyclerView()
        setupClickListeners()
        observeViewModel()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_clear_finished -> {
                viewModel.clearFinishedItems()
                true
            }
            R.id.action_clear_all -> {
                viewModel.clearAllItems()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupPresetDropdown() {
        val presets = CompressionPreset.entries
        val presetNames = presets.map { it.displayName }
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            presetNames
        )
        binding.autoCompletePreset.setAdapter(adapter)
        binding.autoCompletePreset.setOnItemClickListener { _, _, position, _ ->
            val chosen = presets.getOrNull(position) ?: return@setOnItemClickListener
            viewModel.selectPreset(chosen)
        }
    }

    private fun setupRecyclerView() {
        queueAdapter = QueueAdapter(
            onCancelOrRemoveClicked = { item -> viewModel.cancelItem(item.id) },
            onOpenClicked = { outputUri -> openCompressedVideo(outputUri) },
            onShareClicked = { outputUri -> shareCompressedVideo(outputUri) }
        )
        binding.recyclerQueue.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = queueAdapter
            itemAnimator = null
        }
    }

    private fun setupClickListeners() {
        binding.btnPickFiles.setOnClickListener {
            pickVideosLauncher.launch(arrayOf("video/*"))
        }

        binding.btnPickOutputFolder.setOnClickListener {
            pickOutputFolderLauncher.launch(null)
        }

        binding.btnStartOrCancelQueue.setOnClickListener {
            val state = viewModel.uiState.value as? UiState.Ready ?: return@setOnClickListener
            if (state.isProcessing) {
                viewModel.cancelEntireQueue()
            } else {
                viewModel.startQueue()
            }
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        if (state is UiState.Ready) {
                            renderReadyState(state)
                        }
                    }
                }

                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            is UiEvent.ShowMessage -> {
                                Toast.makeText(this@MainActivity, event.message, Toast.LENGTH_SHORT).show()
                            }
                            is UiEvent.QueueFinished -> {
                                val savedFormatted = Formatter.formatShortFileSize(
                                    this@MainActivity,
                                    event.totalSavedBytes
                                )
                                val msg = getString(
                                    R.string.notification_completion_body,
                                    event.completedCount,
                                    savedFormatted
                                )
                                Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
                                postCompletionNotification(msg)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun renderReadyState(state: UiState.Ready) {
        if (binding.autoCompletePreset.text.toString() != state.selectedPreset.displayName) {
            binding.autoCompletePreset.setText(state.selectedPreset.displayName, false)
        }
        binding.tvPresetArgs.text = state.selectedPreset.ffmpegArgs

        binding.btnPickOutputFolder.text = if (state.outputDirDisplayName != null) {
            getString(R.string.output_folder_selected_format, state.outputDirDisplayName)
        } else {
            getString(R.string.select_output_folder)
        }

        binding.autoCompletePreset.isEnabled = !state.isProcessing
        binding.btnPickOutputFolder.isEnabled = !state.isProcessing

        if (state.isProcessing) {
            binding.btnStartOrCancelQueue.text = getString(R.string.cancel_entire_queue)
            binding.btnStartOrCancelQueue.isEnabled = true
        } else {
            binding.btnStartOrCancelQueue.text = getString(R.string.start_compression)
            binding.btnStartOrCancelQueue.isEnabled = state.queue.isNotEmpty()
        }

        queueAdapter.submitList(state.queue)
        binding.tvEmptyQueue.visibility = if (state.queue.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun openCompressedVideo(uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "video/mp4")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.error_no_video_player, Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareCompressedVideo(uri: Uri) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, getString(R.string.action_share)))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun postCompletionNotification(body: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(getString(R.string.notification_completion_title))
            .setContentText(body)
            .setAutoCancel(true)
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "video_compression_done"
        private const val NOTIFICATION_ID = 1001
    }
}