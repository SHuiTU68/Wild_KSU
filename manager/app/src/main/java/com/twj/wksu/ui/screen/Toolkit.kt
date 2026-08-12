package com.twj.wksu.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.ramcosta.composedestinations.navigation.EmptyDestinationsNavigator
import com.twj.wksu.Natives
import com.twj.wksu.R
import com.twj.wksu.ui.LocalScrollState
import com.twj.wksu.ui.util.LocalSnackbarHost
import com.twj.wksu.ui.util.execKsud
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun ToolkitScreen(navigator: DestinationsNavigator) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    val snackBarHost = LocalSnackbarHost.current
    val scrollState = LocalScrollState.current
    val isNavBarHidden = scrollState?.isScrollingDown?.value ?: false
    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + if (isNavBarHidden) 0.dp else 112.dp
    val scope = rememberCoroutineScope()

    var managerUid by remember { mutableStateOf(Natives.getManagerAppid()) }
    var uidInput by rememberSaveable { mutableStateOf("") }
    var verInput by rememberSaveable { mutableStateOf("") }
    var releaseInput by rememberSaveable { mutableStateOf("") }
    var versionInput by rememberSaveable { mutableStateOf("") }
    var mntInput by rememberSaveable { mutableStateOf("") }

    fun runCmd(cmd: String, successMsg: String, failMsg: String, onSuccess: () -> Unit = {}) {
        scope.launch {
            val ok = withContext(Dispatchers.IO) { execKsud(cmd, true) }
            if (ok) onSuccess()
            snackBarHost.showSnackbar(
                message = if (ok) successMsg else failMsg,
                duration = SnackbarDuration.Short
            )
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = {
                    Text(
                        text = stringResource(R.string.toolkit),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black
                    )
                },
                navigationIcon = {
                    IconButton(onClick = dropUnlessResumed { navigator.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                windowInsets = WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                ),
                scrollBehavior = scrollBehavior
            )
        },
        snackbarHost = {
            SnackbarHost(
                hostState = snackBarHost,
                modifier = Modifier.padding(bottom = navBarPadding)
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Top + WindowInsetsSides.Horizontal
        )
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Crown: manager uid & version overrides ──────────────────────
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Star, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.toolkit_crown),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = stringResource(R.string.toolkit_crown_summary),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        leadingContent = { Icon(Icons.Filled.Build, null) },
                        headlineContent = { Text(stringResource(R.string.toolkit_manager_uid)) },
                        supportingContent = { Text(managerUid.toString()) }
                    )
                    OutlinedTextField(
                        value = uidInput,
                        onValueChange = { uidInput = it.filter { c -> c.isDigit() }.take(5) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.toolkit_set_uid_hint)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Button(
                        onClick = {
                            val uid = uidInput.toIntOrNull()
                            if (uid == null || uid !in 10000..20000) {
                                snackBarHost.showSnackbar(
                                    message = stringResource(R.string.toolkit_uid_range),
                                    duration = SnackbarDuration.Short
                                )
                            } else {
                                runCmd(
                                    cmd = "toolkit setuid $uid",
                                    successMsg = stringResource(R.string.toolkit_ok),
                                    failMsg = stringResource(R.string.toolkit_fail),
                                    onSuccess = { managerUid = uid }
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.toolkit_set_uid))
                    }
                    OutlinedTextField(
                        value = verInput,
                        onValueChange = { verInput = it.filter { c -> c.isDigit() }.take(10) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.toolkit_set_ver_hint)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Button(
                        onClick = {
                            val ver = verInput.toIntOrNull()
                            if (ver != null) {
                                runCmd(
                                    cmd = "toolkit setver $ver",
                                    successMsg = stringResource(R.string.toolkit_ok),
                                    failMsg = stringResource(R.string.toolkit_fail)
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.toolkit_set_ver))
                    }
                }
            }
            // ── Uname: spoof kernel release/version ─────────────────────────
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Terminal, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.toolkit_uname),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = stringResource(R.string.toolkit_uname_summary),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = releaseInput,
                        onValueChange = { releaseInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.toolkit_uname_release_hint)) },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = versionInput,
                        onValueChange = { versionInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.toolkit_uname_version_hint)) },
                        singleLine = true
                    )
                    Button(
                        onClick = {
                            if (releaseInput.isBlank() || versionInput.isBlank()) return@Button
                            runCmd(
                                cmd = "toolkit uname \"$releaseInput\" \"$versionInput\"",
                                successMsg = stringResource(R.string.toolkit_ok),
                                failMsg = stringResource(R.string.toolkit_fail)
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.toolkit_uname_apply))
                    }
                    TextButton(
                        onClick = {
                            runCmd(
                                cmd = "toolkit uname default default",
                                successMsg = stringResource(R.string.toolkit_ok),
                                failMsg = stringResource(R.string.toolkit_fail)
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.toolkit_uname_reset))
                    }
                }
            }
            // ── Umount: kernel umount list ──────────────────────────────────
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Terminal, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.toolkit_umount),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = stringResource(R.string.toolkit_umount_summary),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = mntInput,
                        onValueChange = { mntInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.toolkit_umount_mnt_hint)) },
                        singleLine = true
                    )
                    Button(
                        onClick = {
                            if (mntInput.isBlank()) return@Button
                            runCmd(
                                cmd = "kernel umount add ${mntInput.trim()}",
                                successMsg = stringResource(R.string.toolkit_ok),
                                failMsg = stringResource(R.string.toolkit_fail)
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.toolkit_umount_add))
                    }
                    OutlinedButton(
                        onClick = {
                            if (mntInput.isBlank()) return@OutlinedButton
                            runCmd(
                                cmd = "kernel umount del ${mntInput.trim()}",
                                successMsg = stringResource(R.string.toolkit_ok),
                                failMsg = stringResource(R.string.toolkit_fail)
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.toolkit_umount_del))
                    }
                    TextButton(
                        onClick = {
                            runCmd(
                                cmd = "kernel umount wipe",
                                successMsg = stringResource(R.string.toolkit_ok),
                                failMsg = stringResource(R.string.toolkit_fail)
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.toolkit_umount_wipe))
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun ToolkitPreview() {
    ToolkitScreen(EmptyDestinationsNavigator)
}
