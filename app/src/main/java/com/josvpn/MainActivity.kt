package com.josvpn

import android.app.Activity
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import java.io.ByteArrayInputStream
import java.io.BufferedReader
import java.io.InputStreamReader

class MainActivity : AppCompatActivity() {
    private lateinit var backend: GoBackend
    private lateinit var status: TextView
    private lateinit var selectedSummary: TextView
    private lateinit var appsContainer: LinearLayout
    private lateinit var routeAppsToggle: CheckBox
    private val selectedPackages = linkedSetOf<String>()
    private var configText: String? = null
    private var configLabel: String = "No India WireGuard configuration imported"
    private val appChecks = linkedMapOf<String, CheckBox>()

    private val tunnel = object : Tunnel {
        override fun getName(): String = "JOS-VPN-India"
        override fun onStateChange(newState: Tunnel.State) {
            runOnUiThread {
                status.text = when (newState) {
                    Tunnel.State.UP -> "VPN status: Connected"
                    Tunnel.State.DOWN -> "VPN status: Disconnected"
                    else -> "VPN status: Connecting…"
                }
            }
        }
    }

    private val vpnPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) connectVpn()
            else toast("VPN permission was not granted.")
        }

    private val configPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                try {
                    contentResolver.openInputStream(uri)?.use { input ->
                        configText = BufferedReader(InputStreamReader(input)).readText()
                    }
                    if (configText.isNullOrBlank() ||
                        !configText!!.contains("[Interface]") ||
                        !configText!!.contains("[Peer]")) {
                        configText = null
                        toast("That file does not look like a WireGuard .conf file.")
                    } else {
                        configLabel = "WireGuard profile imported"
                        toast("VPN profile imported. Confirm it is for an India server.")
                    }
                    refreshHeader()
                } catch (e: Exception) {
                    configText = null
                    toast("Could not read config: ${e.message ?: "unknown error"}")
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        backend = GoBackend(applicationContext)
        buildUi()
        loadLaunchableApps()
        refreshHeader()
    }

    private fun buildUi() {
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            setBackgroundColor(0xFFF4F7FB.toInt())
        }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        content.addView(TextView(this).apply {
            text = "JOS VPN"
            textSize = 30f
            setTextColor(0xFF12305A.toInt())
            gravity = Gravity.CENTER
            setTypeface(null, android.graphics.Typeface.BOLD)
        }, matchWrap())
        content.addView(TextView(this).apply {
            text = "INDIA • VPN ROUTING • HOTSTAR LAUNCHER"
            textSize = 12f
            setTextColor(0xFF52657D.toInt())
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(16))
        }, matchWrap())

        status = TextView(this).apply {
            text = "VPN status: Disconnected"
            textSize = 18f
            setTextColor(0xFF172B4D.toInt())
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(16), dp(12), dp(16))
            setBackgroundColor(0xFFFFFFFF.toInt())
        }
        content.addView(status, matchWrap())

        val importButton = button("Import India WireGuard .conf") {
            configPicker.launch(arrayOf("text/plain", "application/octet-stream", "*/*"))
        }
        content.addView(importButton, matchWrap())

        selectedSummary = TextView(this).apply {
            textSize = 13f
            setTextColor(0xFF52657D.toInt())
            setPadding(0, dp(6), 0, dp(10))
            gravity = Gravity.CENTER
        }
        content.addView(selectedSummary, matchWrap())

        routeAppsToggle = CheckBox(this).apply {
            text = "Route only selected apps through the VPN"
            textSize = 15f
            setTextColor(0xFF172B4D.toInt())
            isChecked = false
        }
        content.addView(routeAppsToggle, matchWrap())

        content.addView(TextView(this).apply {
            text = "APP ROUTING"
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(0xFF12305A.toInt())
            setPadding(0, dp(14), 0, dp(4))
        }, matchWrap())

        content.addView(TextView(this).apply {
            text = "When enabled, only checked apps use the VPN. Other apps use their normal connection; this is routing control, not a system-wide app-blocking firewall."
            textSize = 12f
            setTextColor(0xFF8A4B08.toInt())
            setPadding(0, 0, 0, dp(8))
        }, matchWrap())

        appsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(4), dp(10), dp(4))
            setBackgroundColor(0xFFFFFFFF.toInt())
        }
        content.addView(appsContainer, matchWrap())

        content.addView(button("Connect VPN") { requestConnect() }, matchWrap())
        content.addView(button("Disconnect VPN") { disconnectVpn() }, matchWrap())
        content.addView(button("Open JioHotstar") { openHotstar() }, matchWrap())

        content.addView(TextView(this).apply {
            text = "Security note: JOS VPN does not include a VPN server. You must import a valid WireGuard configuration from a provider or your own India-hosted server. An India profile does not guarantee Hotstar availability."
            textSize = 12f
            setTextColor(0xFF52657D.toInt())
            setPadding(0, dp(16), 0, dp(10))
        }, matchWrap())

        scroll.addView(content)
        outer.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(outer)
    }

    private fun loadLaunchableApps() {
        appsContainer.removeAllViews()
        appChecks.clear()
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .mapNotNull { ri ->
                val ai = ri.activityInfo?.applicationInfo ?: return@mapNotNull null
                if (ai.packageName == packageName) return@mapNotNull null
                val label = pm.getApplicationLabel(ai).toString()
                Triple(label, ai.packageName, ai)
            }
            .distinctBy { it.second }
            .sortedBy { it.first.lowercase() }

        for ((label, pkg, _) in resolved) {
            val cb = CheckBox(this).apply {
                text = "$label\n$pkg"
                textSize = 13f
                setTextColor(0xFF172B4D.toInt())
                isChecked = false
                setOnCheckedChangeListener { _, checked ->
                    if (checked) selectedPackages.add(pkg) else selectedPackages.remove(pkg)
                    refreshHeader()
                }
            }
            appChecks[pkg] = cb
            appsContainer.addView(cb, matchWrap())
        }
        refreshHeader()
    }

    private fun requestConnect() {
        if (configText.isNullOrBlank()) {
            toast("Import a valid India WireGuard .conf file first.")
            return
        }
        if (routeAppsToggle.isChecked && selectedPackages.isEmpty()) {
            toast("Select at least one app, or turn off app routing.")
            return
        }
        val intent = VpnService.prepare(this)
        if (intent != null) vpnPermissionLauncher.launch(intent) else connectVpn()
    }

    private fun connectVpn() {
        val original = configText ?: run {
            toast("Import a WireGuard profile first.")
            return
        }
        try {
            val configuredText = applyAppRouting(original)
            val config = Config.parse(ByteArrayInputStream(configuredText.toByteArray(Charsets.UTF_8)))
            backend.setState(tunnel, Tunnel.State.UP, config)
            status.text = "VPN status: Connecting…"
        } catch (e: Exception) {
            status.text = "VPN status: Disconnected"
            toast("Connection failed: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun applyAppRouting(raw: String): String {
        val lines = raw.lines().filterNot {
            val t = it.trim()
            t.startsWith("IncludedApplications", true) ||
                t.startsWith("ExcludedApplications", true)
        }
        if (!routeAppsToggle.isChecked) return lines.joinToString("\n")
        val output = mutableListOf<String>()
        var inserted = false
        for (line in lines) {
            if (!inserted && line.trim().equals("[Peer]", ignoreCase = true)) {
                for (pkg in selectedPackages) output.add("IncludedApplications = $pkg")
                inserted = true
            }
            output.add(line)
        }
        if (!inserted) throw IllegalArgumentException("WireGuard config has no [Peer] section.")
        return output.joinToString("\n")
    }

    private fun disconnectVpn() {
        try {
            backend.setState(tunnel, Tunnel.State.DOWN, null)
            status.text = "VPN status: Disconnected"
        } catch (e: Exception) {
            toast("Could not disconnect: ${e.message ?: "unknown error"}")
        }
    }

    private fun openHotstar() {
        val launchIntent = packageManager.getLaunchIntentForPackage("in.startv.hotstar")
        if (launchIntent != null) {
            startActivity(launchIntent)
        } else {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.hotstar.com/in")))
            } catch (_: Exception) {
                toast("Could not open JioHotstar.")
            }
        }
    }

    private fun refreshHeader() {
        if (::selectedSummary.isInitialized) {
            selectedSummary.text = "$configLabel\n${selectedPackages.size} app(s) selected for VPN routing"
        }
    }

    private fun button(label: String, action: () -> Unit): Button =
        Button(this).apply {
            text = label
            isAllCaps = false
            setOnClickListener { action() }
        }

    private fun matchWrap() = LinearLayout.LayoutParams(-1, -2).apply {
        bottomMargin = dp(8)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}
