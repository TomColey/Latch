package com.nathanb.lock

import android.content.Intent
import android.nfc.NfcAdapter
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.nathanb.lock.nfc.NfcResult
import com.nathanb.lock.ui.LockApp
import com.nathanb.lock.ui.viewmodel.LockViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private val viewModel: LockViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (BuildConfig.DEBUG) Log.d(TAG, "onCreate — action=${intent.action}")
        enableEdgeToEdge()

        val isNfcLaunch = intent.action == NfcAdapter.ACTION_NDEF_DISCOVERED ||
            intent.action == NfcAdapter.ACTION_TAG_DISCOVERED

        setContent {
            LockApp(viewModel = viewModel, isNfcLaunch = isNfcLaunch)
        }

        handleNfcIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        viewModel.cleanupUninstalledPackages()
        if (BuildConfig.DEBUG) Log.d(TAG, "onResume — enabling reader mode")
        viewModel.nfcManager.enableReaderMode(this) { tag ->
            lifecycleScope.launch {
                val result = viewModel.nfcManager.handleTag(tag) ?: return@launch
                if (BuildConfig.DEBUG) Log.d(TAG, "ReaderMode — result=$result")
                handleNfcResult(result)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (BuildConfig.DEBUG) Log.d(TAG, "onPause — disabling reader mode")
        viewModel.nfcManager.disableReaderMode(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (BuildConfig.DEBUG) Log.d(TAG, "onNewIntent — action=${intent.action}")
        setIntent(intent)
        handleNfcIntent(intent)
    }

    private fun handleNfcIntent(intent: Intent) {
        if (BuildConfig.DEBUG) Log.d(TAG, "handleNfcIntent — action=${intent.action}")
        lifecycleScope.launch {
            val result = viewModel.nfcManager.handleIntent(intent)
            if (BuildConfig.DEBUG) Log.d(TAG, "handleNfcIntent — result=$result")
            if (result == null) return@launch
            handleNfcResult(result)
        }
    }

    private fun handleNfcResult(result: NfcResult) {
        viewModel.handleNfcResult(result)

        when (result) {
            is NfcResult.TagPaired -> {
                // Toast handled by NfcTagsScreen / OnboardingScreen
            }
            is NfcResult.Started -> {
                val tagInfo = result.tagName?.let { " ($it)" } ?: ""
                Toast.makeText(this, getString(R.string.toast_blocking_on, tagInfo), Toast.LENGTH_SHORT).show()
            }
            is NfcResult.Stopped -> {
                val tagInfo = result.tagName?.let { " ($it)" } ?: ""
                Toast.makeText(this, getString(R.string.toast_blocking_off, tagInfo), Toast.LENGTH_SHORT).show()
            }
            is NfcResult.ModeLatched -> {
                val tagInfo = result.tagName?.let { " ($it)" } ?: ""
                Toast.makeText(this, "Latched$tagInfo", Toast.LENGTH_SHORT).show()
            }
            is NfcResult.ModeUnlatched -> {
                val tagInfo = result.tagName?.let { " ($it)" } ?: ""
                Toast.makeText(this, "Unlatched$tagInfo", Toast.LENGTH_SHORT).show()
            }
            is NfcResult.ModeActivationConflict -> {
                val tagInfo = result.tagName?.let { " ($it)" } ?: ""
                Toast.makeText(
                    this,
                    "Latch assignment conflict$tagInfo — edit Modes",
                    Toast.LENGTH_LONG,
                ).show()
            }
            is NfcResult.ModeActionIgnored -> {
                if (BuildConfig.DEBUG) {
                    Toast.makeText(this, "This Latch has no action here", Toast.LENGTH_SHORT).show()
                }
            }
            is NfcResult.IgnoredNoEscapeActive -> {
                Toast.makeText(this, getString(R.string.toast_no_escape_active), Toast.LENGTH_SHORT).show()
            }
            is NfcResult.UnknownTag -> {
                Toast.makeText(this, getString(R.string.toast_unknown_tag), Toast.LENGTH_SHORT).show()
            }
            is NfcResult.Error -> {
                Toast.makeText(this, getString(result.messageRes), Toast.LENGTH_SHORT).show()
            }
        }
    }
}
