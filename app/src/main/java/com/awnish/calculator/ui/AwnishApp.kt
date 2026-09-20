package com.awnish.calculator.ui

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.awnish.calculator.data.database.PhotoEntity
import com.awnish.calculator.data.repository.GalleryRepository
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.text.DateFormat

@Composable
fun AwnishApp(activity: FragmentActivity) {
    val prefs = remember { activity.getSharedPreferences("settings", Context.MODE_PRIVATE) }

    LaunchedEffect(Unit) {
        if (!prefs.contains("vault_password_hash")) {
            prefs.edit().putString("vault_password_hash", sha256("1234")).apply()
        }
    }

    var vaultOpen by rememberSaveable { mutableStateOf(false) }
    var showChangePassword by remember { mutableStateOf(false) }
    var passwordHash by remember {
        mutableStateOf(
            prefs.getString("vault_password_hash", sha256("1234")) ?: sha256("1234")
        )
    }

    DisposableEffect(activity) {
        val observer = object : androidx.lifecycle.DefaultLifecycleObserver {
            override fun onStop(owner: androidx.lifecycle.LifecycleOwner) {
                vaultOpen = false
            }
        }
        activity.lifecycle.addObserver(observer)
        onDispose { activity.lifecycle.removeObserver(observer) }
    }

    if (vaultOpen) {
        VaultScreen(
            onClose = { vaultOpen = false },
            onChangePassword = { showChangePassword = true }
        )
    } else {
        CalculatorScreen(
            passwordHash = passwordHash,
            onVaultUnlock = { vaultOpen = true },
            onChangePassword = { showChangePassword = true }
        )
    }

    if (showChangePassword) {
        ChangePasswordDialog(
            onDismiss = { showChangePassword = false },
            onSaved = { newHash ->
                prefs.edit().putString("vault_password_hash", newHash).apply()
                passwordHash = newHash
                showChangePassword = false
            }
        )
    }
}

@Composable
private fun ChangePasswordDialog(onDismiss: () -> Unit, onSaved: (String) -> Unit) {
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set new password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Enter a new 4–12 digit password.")
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = {
                        if (it.all(Char::isDigit) && it.length <= 12) {
                            newPassword = it
                            error = null
                        }
                    },
                    label = { Text("New password") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = {
                        if (it.all(Char::isDigit) && it.length <= 12) {
                            confirmPassword = it
                            error = null
                        }
                    },
                    label = { Text("Confirm password") },
                    singleLine = true
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    newPassword.length < 4 -> error = "Password must be at least 4 digits."
                    newPassword != confirmPassword -> error = "Passwords do not match."
                    else -> onSaved(sha256(newPassword))
                }
            }) { Text("Done") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun VaultScreen(
    onClose: () -> Unit,
    onChangePassword: () -> Unit
) {
    val context = LocalContext.current
    val repo = remember { GalleryRepository(context) }
    val scope = rememberCoroutineScope()

    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("All") }
    var selected by remember { mutableStateOf<PhotoEntity?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    var selectedUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var showMoveWarning by remember { mutableStateOf(false) }

    val items by repo.photos(query, category).collectAsStateWithLifecycle(emptyList())

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        selectedUris = uris
        showMoveWarning = uris.isNotEmpty()
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.ArrowBack, "Close vault")
            }
            Column(Modifier.weight(1f)) {
                Text("Private Vault", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Encrypted photos, videos, documents & APK backups",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.Default.Lock, "Locked vault")
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            placeholder = { Text("Search private files") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, null) }
        )

        val categories = listOf("All", "Photos", "Videos", "Documents", "Apps & Games", "Others")
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            lazyItems(categories) { item ->
                FilterChip(
                    selected = category == item,
                    onClick = { category = item },
                    label = { Text(item, maxLines = 1) }
                )
            }
        }

        info?.let {
            Text(it, modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary)
        }
        error?.let {
            Text(it, modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.error)
        }

        if (items.isEmpty()) {
            Box(
                Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Folder, null, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(10.dp))
                    Text(
                        if (category == "All") "Your private vault is empty"
                        else "No " + category + " in the vault",
                        textAlign = TextAlign.Center
                    )
                    Text(
                        "Use + to move files into encrypted app storage.",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(132.dp),
                modifier = Modifier.weight(1f).padding(top = 2.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                items(items, key = { it.id }) { item ->
                    VaultItemCard(item, repo) { selected = item }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onChangePassword,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Password, null)
                Spacer(Modifier.width(5.dp))
                Text("Password")
            }

            ExtendedFloatingActionButton(
                onClick = {
                    error = null
                    info = null
                    picker.launch(arrayOf("*/*"))
                },
                modifier = Modifier.weight(1.25f),
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Move to Vault") }
            )
        }
    }

    selected?.let { item ->
        VaultItemDialog(
            item = item,
            repo = repo,
            onClose = { selected = null },
            onMessage = {
                info = it
                error = null
            },
            onError = {
                error = it
                info = null
            }
        )
    }

    if (showMoveWarning) {
        AlertDialog(
            onDismissRequest = {
                showMoveWarning = false
                selectedUris = emptyList()
            },
            title = { Text("Move selected files?") },
            text = {
                Text(
                    "AWNISH first creates an encrypted copy inside the private vault. " +
                        "After the copy succeeds, it asks Android to remove the original. " +
                        "If the source provider does not allow deletion, the encrypted copy is kept and the original remains."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val uris = selectedUris
                    showMoveWarning = false
                    selectedUris = emptyList()
                    scope.launch {
                        var moved = 0
                        var copiedOnly = 0
                        var failed = 0

                        uris.forEach { uri ->
                            runCatching { repo.import(uri, removeOriginal = true) }
                                .onSuccess { removed ->
                                    if (removed) moved++ else copiedOnly++
                                }
                                .onFailure { failed++ }
                        }

                        info = buildString {
                            if (moved > 0) append(moved.toString() + " moved securely. ")
                            if (copiedOnly > 0) append(copiedOnly.toString() + " encrypted copy/copies created; original could not be removed. ")
                            if (failed > 0) append(failed.toString() + " file(s) failed.")
                        }.trim()

                        if (failed > 0) error = "Some files could not be imported."
                    }
                }) { Text("Move & Encrypt") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showMoveWarning = false
                    selectedUris = emptyList()
                }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun VaultItemCard(
    item: PhotoEntity,
    repo: GalleryRepository,
    open: () -> Unit
) {
    var bitmap by remember(item.id) { mutableStateOf<android.graphics.Bitmap?>(null) }

    LaunchedEffect(item.id) {
        if (item.category == "Photos") {
            bitmap = runCatching {
                val raw = repo.bytes(item)
                BitmapFactory.decodeByteArray(raw, 0, raw.size)
            }.getOrNull()
        }
    }

    Card(
        Modifier.fillMaxWidth().height(145.dp).clickable { open() }
    ) {
        Column {
            Box(
                Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                when {
                    bitmap != null -> Image(
                        bitmap!!.asImageBitmap(),
                        item.displayName,
                        Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    item.category == "Videos" -> Icon(Icons.Default.Movie, "Video", modifier = Modifier.size(46.dp))
                    item.category == "Documents" -> Icon(Icons.Default.Description, "Document", modifier = Modifier.size(46.dp))
                    item.category == "Apps & Games" -> Icon(Icons.Default.Apps, "App or game APK", modifier = Modifier.size(46.dp))
                    else -> Icon(Icons.Default.Folder, "Other file", modifier = Modifier.size(46.dp))
                }
            }
            Text(
                item.displayName,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                maxLines = 1,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@Composable
private fun VaultItemDialog(
    item: PhotoEntity,
    repo: GalleryRepository,
    onClose: () -> Unit,
    onMessage: (String) -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var image by remember(item.id) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(item.id) {
        if (item.category == "Photos") {
            runCatching {
                val raw = repo.bytes(item)
                BitmapFactory.decodeByteArray(raw, 0, raw.size)
            }.onSuccess { image = it }
                .onFailure { onError("Could not decrypt this image.") }
        }
    }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(item.displayName) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (image != null) {
                    Image(
                        image!!.asImageBitmap(),
                        item.displayName,
                        Modifier.fillMaxWidth().heightIn(max = 420.dp),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Icon(
                        when (item.category) {
                            "Videos" -> Icons.Default.Movie
                            "Documents" -> Icons.Default.Description
                            "Apps & Games" -> Icons.Default.Apps
                            else -> Icons.Default.Folder
                        },
                        null,
                        modifier = Modifier.align(Alignment.CenterHorizontally).size(58.dp)
                    )
                }

                Text("Category: " + item.category)
                Text(item.mimeType + " • " + formatBytes(item.sizeBytes))
                Text(DateFormat.getDateTimeInstance().format(item.createdAt))

                if (item.category == "Apps & Games") {
                    Text(
                        "APK backup stored securely. A normal Android app cannot move an already-installed app inside itself; installed-app hiding requires system/device-owner controls."
                    )
                }
            }
        },
        confirmButton = {
            if (item.category != "Apps & Games") {
                TextButton(onClick = {
                    val uri = repo.fileUri(item)
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, item.mimeType)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    runCatching { context.startActivity(intent) }
                        .onSuccess { onMessage("Opened " + item.displayName) }
                        .onFailure { onError("No compatible app found to open this file.") }
                }) {
                    Icon(Icons.Default.OpenInNew, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Open")
                }
            }

            TextButton(onClick = {
                scope.launch {
                    runCatching { repo.export(item) }
                        .onSuccess { onMessage("Exported to Download/AWNISH Vault") }
                        .onFailure { onError("Export failed: " + it.message) }
                }
            }) {
                Text("Export")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onClose) { Text("Close") }
                TextButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Default.Delete, null)
                    Spacer(Modifier.width(3.dp))
                    Text("Delete")
                }
            }
        }
    )

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete from private vault?") },
            text = {
                Text("This permanently deletes the encrypted copy from AWNISH. It does not delete any other copy that may still exist elsewhere.")
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        repo.permanentlyDelete(item)
                        confirmDelete = false
                        onClose()
                        onMessage("Deleted permanently from the private vault.")
                    }
                }) { Text("Delete permanently") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            }
        )
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return bytes.toString() + " B"
    if (bytes < 1024 * 1024) return (bytes / 1024).toString() + " KB"
    if (bytes < 1024 * 1024 * 1024) return (bytes / (1024 * 1024)).toString() + " MB"
    return (bytes / (1024 * 1024 * 1024)).toString() + " GB"
}

private fun sha256(value: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    return digest.digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
