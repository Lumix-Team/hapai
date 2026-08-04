package studio.creater120.hapai.fragments

import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import studio.creater120.hapai.R
import studio.creater120.hapai.activities.SettingsActivity
import studio.creater120.hapai.activities.TestActivity
import studio.creater120.hapai.core.TProxyService
import studio.creater120.hapai.data.*
import studio.creater120.hapai.databinding.FragmentHapaiVpnBinding
import studio.creater120.hapai.databinding.ItemServerBinding
import studio.creater120.hapai.services.HapaiVpnService
import studio.creater120.hapai.services.ServiceManager
import studio.creater120.hapai.services.appStatus
import studio.creater120.hapai.utility.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class HapaiVpnFragment : Fragment() {
    private var _binding: FragmentHapaiVpnBinding? = null
    private val binding get() = _binding!!

    private var timerJob: Job? = null
    private var statsJob: Job? = null
    private var connectionStartTime: Long = 0L

    private val vpnRegister =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == android.app.Activity.RESULT_OK) {
                ServiceManager.start(requireContext(), Mode.VPN)
            } else {
                Toast.makeText(requireContext(), R.string.vpn_permission_denied, Toast.LENGTH_SHORT).show()
                updateUI()
            }
        }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            when (intent.action) {
                STARTED_BROADCAST -> { updateUI(); startTimerAndStats() }
                STOPPED_BROADCAST -> { stopJobs(); updateUI() }
                FAILED_BROADCAST -> {
                    stopJobs()
                    Toast.makeText(requireContext(), getString(R.string.failed_to_start, "VPN"), Toast.LENGTH_SHORT).show()
                    updateUI()
                }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHapaiVpnBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.connectButton.setOnClickListener {
            binding.connectButton.isClickable = false
            when (appStatus.first) {
                AppStatus.Halted -> start()
                AppStatus.Running -> stop()
            }
            binding.connectButton.postDelayed({ binding.connectButton.isClickable = true }, 1000)
        }

        binding.editorButton.setOnClickListener {
            val useCmdSettings = requireContext().getPreferences().getCmdEnable()
            if (!useCmdSettings && appStatus.first == AppStatus.Running) {
                Toast.makeText(requireContext(), R.string.settings_unavailable, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val intent = Intent(requireContext(), SettingsActivity::class.java)
            intent.putExtra("open_fragment", if (useCmdSettings) "cmd" else "ui")
            startActivity(intent)
        }

        binding.testProxyButton.setOnClickListener {
            startActivity(Intent(requireContext(), TestActivity::class.java))
        }

        binding.settingsButton.setOnClickListener {
            if (appStatus.first == AppStatus.Halted) {
                startActivity(Intent(requireContext(), SettingsActivity::class.java))
            } else {
                Toast.makeText(requireContext(), R.string.settings_unavailable, Toast.LENGTH_SHORT).show()
            }
        }

        binding.quickTestButton.setOnClickListener {
            startActivity(Intent(requireContext(), TestActivity::class.java))
        }

        binding.importButton.setOnClickListener {
            showImportDialog()
        }


    }

    override fun onResume() {
        super.onResume()
        updateUI()
        val intentFilter = IntentFilter().apply {
            addAction(STARTED_BROADCAST); addAction(STOPPED_BROADCAST); addAction(FAILED_BROADCAST)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requireActivity().registerReceiver(receiver, intentFilter, android.content.Context.RECEIVER_EXPORTED)
        } else {
            requireActivity().registerReceiver(receiver, intentFilter)
        }
        if (appStatus.first == AppStatus.Running) startTimerAndStats()
        refreshServerList()
    }

    override fun onPause() {
        super.onPause()
        try { requireActivity().unregisterReceiver(receiver) } catch (_: Exception) {}
        stopJobs()
    }

    override fun onDestroyView() {
        super.onDestroyView(); _binding = null
    }

    private fun start() {
        val mode = requireContext().getPreferences().mode()
        when (mode) {
            Mode.VPN -> {
                val server = ServerManager.getActiveServer(requireContext())
                if (server == null) {
                    Toast.makeText(requireContext(), "Выберите сервер VLESS", Toast.LENGTH_SHORT).show()
                    return
                }
                val configJson = happicore.Happicore.generateSocksConfig(server.link, 1080)

                val intentPrepare = VpnService.prepare(requireContext())
                if (intentPrepare != null) {
                    vpnRegister.launch(intentPrepare)
                } else {
                    val intent = Intent(requireContext(), HapaiVpnService::class.java)
                    intent.action = START_ACTION
                    intent.putExtra(EXTRA_CONFIG_JSON, configJson)
                    ContextCompat.startForegroundService(requireContext(), intent)
                }
            }
            Mode.Proxy -> ServiceManager.start(requireContext(), Mode.Proxy)
        }
    }

    private fun stop() = ServiceManager.stop(requireContext())

    private fun updateUI() {
        val (status, mode) = appStatus
        val (ip, port) = requireContext().getPreferences().getProxyIpAndPort()
        binding.proxyAddress.text = getString(R.string.proxy_address, ip, port)
        binding.modeBadge.text = mode.name

        when (status) {
            AppStatus.Halted -> {
                val typedValue = android.util.TypedValue()
                requireActivity().theme.resolveAttribute(android.R.attr.colorPrimary, typedValue, true)
                binding.connectButton.setCardBackgroundColor(typedValue.data)
                binding.connectIcon.clearColorFilter()
                binding.statusText.text = when (mode) {
                    Mode.VPN -> getString(R.string.vpn_disconnected)
                    Mode.Proxy -> getString(R.string.proxy_down)
                }
                binding.timerText.visibility = View.GONE
            }
            AppStatus.Running -> {
                binding.connectButton.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.green_active))
                binding.connectIcon.setColorFilter(ContextCompat.getColor(requireContext(), android.R.color.white))
                binding.statusText.text = when (mode) {
                    Mode.VPN -> getString(R.string.vpn_connected)
                    Mode.Proxy -> getString(R.string.proxy_up)
                }
            }
        }
    }

    private fun startTimerAndStats() {
        if (appStatus.first != AppStatus.Running) return
        connectionStartTime = System.currentTimeMillis()
        binding.timerText.visibility = View.VISIBLE

        timerJob?.cancel()
        timerJob = lifecycleScope.launch(Dispatchers.Main) {
            while (isActive) {
                if (appStatus.first != AppStatus.Running) break
                val elapsed = System.currentTimeMillis() - connectionStartTime
                val totalSecs = elapsed / 1000
                val h = totalSecs / 3600; val m = (totalSecs % 3600) / 60; val s = totalSecs % 60
                binding.timerText.text = if (h > 0) String.format("%02d:%02d:%02d", h, m, s)
                else String.format("%02d:%02d", m, s)
                delay(1000)
            }
        }

        statsJob?.cancel()
        statsJob = lifecycleScope.launch(Dispatchers.Main) {
            while (isActive) {
                if (appStatus.first != AppStatus.Running) break
                try {
                    val stats = TProxyService.TProxyGetStats()
                    if (stats != null && stats.size >= 4) {
                        binding.rxText.text = formatBytes(stats[3])
                        binding.txText.text = formatBytes(stats[1])
                    }
                } catch (_: Exception) {}
                delay(2000)
            }
        }
    }

    private fun stopJobs() {
        timerJob?.cancel(); timerJob = null
        statsJob?.cancel(); statsJob = null
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes < 1024 -> "$bytes Б"
        bytes < 1024 * 1024 -> String.format("%.1f КБ", bytes / 1024.0)
        bytes < 1024 * 1024 * 1024 -> String.format("%.1f МБ", bytes / (1024.0 * 1024))
        else -> String.format("%.1f ГБ", bytes / (1024.0 * 1024 * 1024))
    }

    // --- Server management ---

    private fun showImportDialog() {
        val options = arrayOf("Из буфера обмена", "Ввести вручную")
        AlertDialog.Builder(requireContext())
            .setTitle("Импорт сервера")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> importFromClipboard()
                    1 -> showManualInput()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun importFromClipboard() {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: run {
            Toast.makeText(requireContext(), "Буфер обмена пуст", Toast.LENGTH_SHORT).show()
            return
        }
        if (ServerManager.importFromClipboard(requireContext(), text)) {
            Toast.makeText(requireContext(), "Сервер импортирован", Toast.LENGTH_SHORT).show()
            refreshServerList()
        } else {
            Toast.makeText(requireContext(), "Неверная ссылка", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showManualInput() {
        val input = EditText(requireContext()).apply {
            hint = "vless://..."
            setText("vless://")
            setSelection(8)
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Ввести ссылку вручную")
            .setView(input)
            .setPositiveButton("Импорт") { _, _ ->
                val text = input.text.toString().trim()
                if (ServerManager.importFromClipboard(requireContext(), text)) {
                    Toast.makeText(requireContext(), "Сервер импортирован", Toast.LENGTH_SHORT).show()
                    refreshServerList()
                } else {
                    Toast.makeText(requireContext(), "Неверная ссылка", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun refreshServerList() {
        binding.serverList.removeAllViews()
        val servers = ServerManager.getServers(requireContext())
        val activeServer = ServerManager.getActiveServer(requireContext())

        for (server in servers) {
            val itemBinding = ItemServerBinding.inflate(layoutInflater, binding.serverList, false)
            itemBinding.serverName.text = server.name
            itemBinding.serverAddress.text = "${server.address}:${server.port}"
            itemBinding.serverCheck.visibility = if (server.id == activeServer?.id) View.VISIBLE else View.GONE

            itemBinding.root.setOnClickListener {
                if (appStatus.first == AppStatus.Running) {
                    Toast.makeText(requireContext(), "Сначала отключитесь", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (server.id == activeServer?.id) {
                    ServerManager.setActiveServer(requireContext(), null)
                    Toast.makeText(requireContext(), "Сервер снят", Toast.LENGTH_SHORT).show()
                } else {
                    ServerManager.setActiveServer(requireContext(), server)
                    Toast.makeText(requireContext(), "Выбран: ${server.name}", Toast.LENGTH_SHORT).show()
                }
                refreshServerList()
            }

            itemBinding.root.setOnLongClickListener {
                AlertDialog.Builder(requireContext())
                    .setTitle(server.name)
                    .setMessage("${server.address}:${server.port}")
                    .setPositiveButton("Удалить") { _, _ ->
                        if (server.id == activeServer?.id) ServerManager.setActiveServer(requireContext(), null)
                        ServerManager.removeServer(requireContext(), server.id)
                        refreshServerList()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
                true
            }

            binding.serverList.addView(itemBinding.root)
        }
    }
}
