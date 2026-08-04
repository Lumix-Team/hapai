package studio.creater120.hapai.fragments

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import studio.creater120.hapai.R
import studio.creater120.hapai.data.*
import studio.creater120.hapai.databinding.FragmentByedpiBinding
import studio.creater120.hapai.services.ServiceManager
import studio.creater120.hapai.services.appStatus
import studio.creater120.hapai.utility.*

class ByeDpiFragment : Fragment() {
    private var _binding: FragmentByedpiBinding? = null
    private val binding get() = _binding!!
    private lateinit var historyUtils: HistoryUtils

    private val vpnRegister =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == android.app.Activity.RESULT_OK) {
                ServiceManager.start(requireContext(), Mode.VPN)
            } else {
                Toast.makeText(requireContext(), R.string.vpn_permission_denied, Toast.LENGTH_SHORT).show()
                updateStatus()
            }
        }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            val action = intent.action
            if (action == STARTED_BROADCAST || action == STOPPED_BROADCAST || action == FAILED_BROADCAST) {
                handleServiceEvent(action)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentByedpiBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        historyUtils = HistoryUtils(requireContext())

        binding.statusButtonCard.setOnClickListener {
            binding.statusButtonCard.isClickable = false
            val (status, _) = appStatus
            when (status) {
                AppStatus.Halted -> start()
                AppStatus.Running -> stop()
            }
            binding.statusButtonCard.postDelayed({ binding.statusButtonCard.isClickable = true }, 1000)
        }

        binding.settingsButton.setOnClickListener {
            if (appStatus.first == AppStatus.Halted) {
                startActivity(Intent(requireContext(), studio.creater120.hapai.activities.SettingsActivity::class.java))
            } else {
                Toast.makeText(requireContext(), R.string.settings_unavailable, Toast.LENGTH_SHORT).show()
            }
        }

        binding.editorButton.setOnClickListener {
            val useCmdSettings = requireContext().getPreferences().getCmdEnable()
            if (!useCmdSettings && appStatus.first == AppStatus.Running) {
                Toast.makeText(requireContext(), R.string.settings_unavailable, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val intent = Intent(requireContext(), studio.creater120.hapai.activities.SettingsActivity::class.java)
            intent.putExtra("open_fragment", if (useCmdSettings) "cmd" else "ui")
            startActivity(intent)
        }

        binding.testProxyButton.setOnClickListener {
            startActivity(Intent(requireContext(), studio.creater120.hapai.activities.TestActivity::class.java))
        }

        binding.domainListsButton.setOnClickListener {
            val intent = Intent(requireContext(), studio.creater120.hapai.activities.TestSettingsActivity::class.java)
            intent.putExtra("open_fragment", "domain_lists")
            startActivity(intent)
        }

        binding.strategyButton.setOnClickListener {
            showStrategyPicker()
        }

        binding.importButton.setOnClickListener {
            val useCmdSettings = requireContext().getPreferences().getCmdEnable()
            val intent = Intent(requireContext(), studio.creater120.hapai.activities.SettingsActivity::class.java)
            intent.putExtra("open_fragment", if (useCmdSettings) "cmd" else "ui")
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
        updateStrategyButton()
        val intentFilter = IntentFilter().apply {
            addAction(STARTED_BROADCAST)
            addAction(STOPPED_BROADCAST)
            addAction(FAILED_BROADCAST)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requireActivity().registerReceiver(receiver, intentFilter, android.content.Context.RECEIVER_EXPORTED)
        } else {
            requireActivity().registerReceiver(receiver, intentFilter)
        }
    }

    override fun onPause() {
        super.onPause()
        try { requireActivity().unregisterReceiver(receiver) } catch (_: Exception) {}
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun handleServiceEvent(action: String) {
        when (action) {
            STARTED_BROADCAST, STOPPED_BROADCAST -> {
                updateStatus()
            }
            FAILED_BROADCAST -> {
                Toast.makeText(requireContext(), getString(R.string.failed_to_start, "ByeDPI"), Toast.LENGTH_SHORT).show()
                updateStatus()
            }
        }
    }

    private fun start() {
        val mode = requireContext().getPreferences().mode()
        when (mode) {
            Mode.VPN -> {
                val intentPrepare = VpnService.prepare(requireContext())
                if (intentPrepare != null) {
                    vpnRegister.launch(intentPrepare)
                } else {
                    ServiceManager.start(requireContext(), Mode.VPN)
                }
            }
            Mode.Proxy -> ServiceManager.start(requireContext(), Mode.Proxy)
        }
    }

    private fun stop() {
        ServiceManager.stop(requireContext())
    }

    private fun updateStatus() {
        val (status, mode) = appStatus
        val (ip, port) = requireContext().getPreferences().getProxyIpAndPort()
        binding.proxyAddress.text = getString(R.string.proxy_address, ip, port)

        when (status) {
            AppStatus.Halted -> {
                val typedValue = android.util.TypedValue()
                requireActivity().theme.resolveAttribute(android.R.attr.colorPrimary, typedValue, true)
                binding.statusButtonCard.setCardBackgroundColor(typedValue.data)
                binding.statusButtonIcon.clearColorFilter()
                binding.statusText.setText(
                    when (requireContext().getPreferences().mode()) {
                        Mode.VPN -> R.string.vpn_disconnected
                        Mode.Proxy -> R.string.proxy_down
                    }
                )
            }
            AppStatus.Running -> {
                binding.statusButtonCard.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.green_active))
                binding.statusButtonIcon.setColorFilter(ContextCompat.getColor(requireContext(), android.R.color.white))
                binding.statusText.setText(
                    when (mode) {
                        Mode.VPN -> R.string.vpn_connected
                        Mode.Proxy -> R.string.proxy_up
                    }
                )
            }
        }
    }

    private fun updateStrategyButton() {
        val useCmdSettings = requireContext().getPreferences().getCmdEnable()
        if (!useCmdSettings) {
            binding.cmdButtonsRow.visibility = View.GONE
            binding.strategyButton.visibility = View.GONE
            return
        }
        binding.cmdButtonsRow.visibility = View.VISIBLE
        val pinned = historyUtils.getPinnedHistory()
        val currentCmdArgs = requireContext().getPreferences().getString("byedpi_cmd_args", "") ?: ""
        if (pinned.isEmpty() || currentCmdArgs.isBlank()) {
            binding.strategyButton.visibility = View.GONE
            return
        }
        val matched = pinned.find { it.text == currentCmdArgs }
        val label = when {
            matched == null -> currentCmdArgs
            matched.name.isNullOrBlank() -> matched.text
            else -> "${matched.name}: ${matched.text}"
        }
        binding.strategyButtonText.text = label
        binding.strategyButton.visibility = View.VISIBLE
    }

    private fun showStrategyPicker() {
        val pinned = historyUtils.getPinnedHistory()
        if (pinned.isEmpty()) return
        val adapter = object : ArrayAdapter<Command>(
            requireContext(),
            R.layout.item_main_strategy,
            pinned
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = convertView ?: layoutInflater.inflate(R.layout.item_main_strategy, parent, false)
                val command = getItem(position)!!
                val nameView = view.findViewById<TextView>(R.id.strategyName)
                val textView = view.findViewById<TextView>(R.id.strategyText)
                val name = command.name?.takeIf { it.isNotBlank() }
                if (name != null) {
                    nameView.visibility = View.VISIBLE
                    nameView.text = name
                    textView.maxLines = 2
                } else {
                    nameView.visibility = View.GONE
                    textView.maxLines = 3
                }
                textView.text = command.text
                return view
            }
        }
        val listView = ListView(requireContext()).apply {
            divider = null
            this.adapter = adapter
        }
        val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.main_strategy_picker))
            .setView(listView)
            .setNegativeButton(getString(android.R.string.cancel), null)
            .create()
        listView.setOnItemClickListener { _, _, position, _ ->
            applyStrategy(pinned[position].text)
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun applyStrategy(commandText: String) {
        requireContext().getPreferences().edit { putString("byedpi_cmd_args", commandText) }
        updateStrategyButton()
        if (appStatus.first == AppStatus.Running) {
            val mode = requireContext().getPreferences().mode()
            if (mode == Mode.VPN && VpnService.prepare(requireContext()) != null) return
            ServiceManager.restart(requireContext(), mode)
            Toast.makeText(requireContext(), R.string.service_restart, Toast.LENGTH_SHORT).show()
        }
    }
}
