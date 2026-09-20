package com.awnish.calculator.ui

import android.content.Context
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.awnish.calculator.data.database.PhotoEntity
import com.awnish.calculator.data.repository.GalleryRepository
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.text.DateFormat

private enum class Section(val title: String) {
    CALCULATOR("Calculator"),
    SETTINGS("Settings")
}

@Composable
fun AwnishApp(activity: FragmentActivity) {
    val prefs = remember {
        activity.getSharedPreferences("settings", Context.MODE_PRIVATE)
    }

    LaunchedEffect(Unit) {
        if (!prefs.contains("vault_password_hash")) {
            prefs.edit().putString("vault_password_hash", sha256("1234")).apply()
        }
    }

    var section by rememberSaveable { mutableStateOf(Section.CALCULATOR) }
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
        Scaffold(
            bottomBar = {
                NavigationBar {
                    Section.entries.forEach { s ->
                        NavigationBarItem(
                            selected = section == s,
                            onClick = { section = s },
                            icon = {
                                Icon(
                                    when (s) {
                                        Section.CALCULATOR -> Icons.Default.Calculate
                                        Section.SETTINGS -> Icons.Default.Settings
                                    },
                                    contentDescription = s.title
                                )
                            },
                            label = { Text(s.title) }
                        )
                    }
                }
            }
        ) { pad ->
            Box(Modifier.padding(pad)) {
                when (section) {
                    Section.CALCULATOR -> CalculatorScreen(
                        passwordHash = passwordHash,
                        onVaultUnlock = { vaultOpen = true },
                        onChangePassword = { showChangePassword = true }
                    )
                    Section.SETTINGS -> SettingsScreen()
                }
            }
        }
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
private fun ChangePasswordDialog(
    onDismiss: () -> Unit,
    onSaved: (String) -> Unit
) {
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
            TextButton(
                onClick = {
                    when {
                        newPassword.length < 4 ->
                            error = "Password must be at least 4 digits."
                        newPassword != confirmPassword ->
                            error = "Passwords do not match."
                        else ->
                            onSaved(sha256(newPassword))
                    }
                }
            ) {
                Text("Done")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
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
    val photos by repo.photos(query).collectAsStateWithLifecycle(emptyList())
    var selected by remember { mutableStateOf<PhotoEntity?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(50)
    ) { uris ->
        scope.launch {
            uris.forEach {
                runCatching { repo.import(it) }
                    .onFailure { error = it.message ?: "Import failed." }
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Close vault")
            }
            Text(
                "Private Vault",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f)
            )
            Icon(Icons.Default.Lock, contentDescription = "Locked vault")
        }

        Text(
            "Private photos are available only after the calculator password is entered.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            placeholder = { Text("Search private photos") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, null) }
        )

        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        if (photos.isEmpty()) {
            Box(
                Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No private photos yet\nAdd photos to store an encrypted copy.",
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(112.dp),
                modifier = Modifier.weight(1f).padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(photos, key = { it.id }) { photo ->
                    PhotoCard(photo, repo) { selected = photo }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onChangePassword,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Password, null)
                Spacer(Modifier.width(6.dp))
                Text("Change password")
            }

            ExtendedFloatingActionButton(
                onClick = {
                    picker.launch(
                        PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.ImageOnly
                        )
                    )
                },
                modifier = Modifier.weight(1f),
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Add photos") }
            )
        }
    }

    selected?.let { photo ->
        PhotoViewer(
            photo,
            repo,
            onClose = { selected = null },
            onError = { error = it }
        )
    }
}

@Composable
private fun PhotoCard(
    photo: PhotoEntity,
    repo: GalleryRepository,
    open: () -> Unit
) {
    var bitmap by remember(photo.id) {
        mutableStateOf<android.graphics.Bitmap?>(null)
    }

    LaunchedEffect(photo.id) {
        bitmap = runCatching {
            val bytes = repo.bytes(photo)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
    }

    Card(Modifier.aspectRatio(1f).clickable { open() }) {
        bitmap?.let {
            Image(
                it.asImageBitmap(),
                photo.displayName,
                Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } ?: Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Image, "Encrypted image loading")
        }
    }
}

@Composable
private fun PhotoViewer(
    photo: PhotoEntity,
    repo: GalleryRepository,
    onClose: () -> Unit,
    onError: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var bitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var confirm by remember { mutableStateOf(false) }

    LaunchedEffect(photo.id) {
        runCatching { repo.bytes(photo) }
            .onSuccess {
                bitmap = BitmapFactory.decodeByteArray(it, 0, it.size)
            }
            .onFailure {
                onError("Could not decrypt this image.")
            }
    }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(photo.displayName) },
        text = {
            Column {
                bitmap?.let {
                    Image(
                        it.asImageBitmap(),
                        photo.displayName,
                        Modifier.fillMaxWidth().heightIn(max = 480.dp),
                        contentScale = ContentScale.Fit
                    )
                }
                Text(
                    photo.mimeType + " • " +
                        DateFormat.getDateTimeInstance().format(photo.createdAt)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    scope.launch {
                        runCatching { repo.export(photo) }
                            .onSuccess { onError("Exported to Pictures/AWNISH Exports") }
                            .onFailure { onError("Export failed: " + it.message) }
                    }
                }
            ) {
                Text("Export")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onClose) { Text("Close") }
                TextButton(onClick = { confirm = true }) { Text("Delete") }
            }
        }
    )

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Move photo to trash?") },
            text = {
                Text(
                    "The encrypted copy will be marked deleted. " +
                        "This does not affect the original photo."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            repo.trash(photo)
                            onClose()
                        }
                    }
                ) {
                    Text("Move to trash")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SettingsScreen() {
    Column(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        Text("Private Vault", style = MaterialTheme.typography.titleMedium)
        Text(
            "Default password on first install: 1234. " +
                "Enter it in the calculator and press = to open the vault."
        )
        Text(
            "Password change shortcut: enter 1234 + 1234 and press =, " +
                "then enter and confirm the new 4–12 digit password."
        )
        HorizontalDivider()
        Text("Privacy", style = MaterialTheme.typography.titleMedium)
        Text(
            "Private photos are encrypted and stored only in app-private storage. " +
                "The app does not upload them."
        )
        Text("About", style = MaterialTheme.typography.titleMedium)
        Text("AWNISH Calculator 1.1.0")
    }
}

private fun sha256(value: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    return digest.digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
