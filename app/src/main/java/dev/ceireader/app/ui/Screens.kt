package dev.ceireader.app.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.Settings
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ceireader.app.BuildConfig
import dev.ceireader.app.model.AddressPeriod
import dev.ceireader.app.model.CeiData
import dev.ceireader.app.model.NfcStatus
import dev.ceireader.app.model.ReadErrorKind
import dev.ceireader.app.model.ReadState
import dev.ceireader.app.model.Validation
import dev.ceireader.app.pdf.CeiPdfExporter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Top-level screen switch driven by [ReadViewModel.state]. There is
 * intentionally no navigation stack -- the four states map 1:1 to a full
 * screen each, and [ReadViewModel.reset] is the only way back to [IdleScreen].
 */
@Composable
fun CeiApp(vm: ReadViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (val s = state) {
            is ReadState.Idle -> IdleScreen(vm)
            is ReadState.Started, is ReadState.ReadingCard -> ReadingScreen()
            is ReadState.Finished -> ResultScreen(data = s.data, onReset = vm::reset)
            is ReadState.Error -> ErrorScreen(kind = s.kind, retriesLeft = s.retriesLeft, onRetry = vm::reset)
        }
    }
}

// ---------------------------------------------------------------------------
// Idle / entry screen
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IdleScreen(vm: ReadViewModel) {
    var can by remember { mutableStateOf(vm.can) }
    var pin by remember { mutableStateOf(vm.pin) }
    var includePhoto by remember { mutableStateOf(vm.includePhoto) }

    val canValid = Validation.isValidCan(can)
    val pinValid = Validation.isValidPin(pin)
    val nfcStatus = vm.nfcStatus
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Filled.CreditCard,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "CEI Reader",
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Citește datele de pe cartea electronică de identitate prin NFC.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(32.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    OutlinedTextField(
                        value = can,
                        onValueChange = { new ->
                            val filtered = new.filter { it.isDigit() }.take(6)
                            can = filtered
                            vm.can = filtered
                        },
                        label = { Text("CAN") },
                        supportingText = { Text("Codul CAN (6 cifre) de pe fața cardului") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(Modifier.height(16.dp))

                    OutlinedTextField(
                        value = pin,
                        onValueChange = { new ->
                            val filtered = new.filter { it.isDigit() }.take(4)
                            pin = filtered
                            vm.pin = filtered
                            if (filtered.length == 4) {
                                // Auto-hide the keyboard once the 4-digit PIN
                                // is complete: clearFocus() alone can leave
                                // the IME visible on some devices, so also
                                // explicitly hide it via the keyboard controller.
                                focusManager.clearFocus()
                                keyboardController?.hide()
                            }
                        },
                        label = { Text("PIN") },
                        supportingText = { Text("PIN-ul cardului din 4 cifre") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.NumberPassword,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Include fotografia (mai lent)",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = includePhoto,
                            onCheckedChange = { checked ->
                                includePhoto = checked
                                vm.includePhoto = checked
                            },
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    when {
                        nfcStatus == NfcStatus.NO_HARDWARE -> Text(
                            text = "Acest dispozitiv nu are NFC.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        nfcStatus == NfcStatus.DISABLED -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.WifiOff,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "NFC este dezactivat. Activați-l pentru a citi cardul.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                    textAlign = TextAlign.Center,
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            OutlinedButton(onClick = { openNfcSettings(context) }) {
                                Text("Activează NFC")
                            }
                        }

                        canValid && pinValid -> Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Nfc,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Pregătit. Apropiați cardul de spatele telefonului.",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = "v${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Opens system NFC settings, falling back to wireless settings on devices without a dedicated screen. */
private fun openNfcSettings(context: android.content.Context) {
    val intent = Intent(Settings.ACTION_NFC_SETTINGS)
    if (intent.resolveActivity(context.packageManager) != null) {
        context.startActivity(intent)
    } else {
        context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
    }
}

// ---------------------------------------------------------------------------
// Reading screen -- the key "hold still" UX
// ---------------------------------------------------------------------------

@Composable
private fun ReadingScreen() {
    val transition = rememberInfiniteTransition(label = "pulse")
    val scale by transition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "scale",
    )

    Box(
        modifier = Modifier.fillMaxSize().systemBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                // The gold accent is reserved for exactly this moment -- the
                // one place where the app is actively broadcasting/reading --
                // so it reads as a deliberate signal, not decoration.
                CircularProgressIndicator(
                    modifier = Modifier.size(140.dp),
                    strokeWidth = 4.dp,
                    color = MaterialTheme.colorScheme.tertiary,
                    trackColor = MaterialTheme.colorScheme.tertiaryContainer,
                )
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .scale(scale)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.tertiaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Nfc,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(44.dp),
                    )
                }
            }

            Spacer(Modifier.height(40.dp))

            Text(
                text = "Țineți cardul nemișcat",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = "Sprijiniți cardul pe spatele telefonului și nu îl mișcați până la finalizare.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Result screen
// ---------------------------------------------------------------------------

@Composable
private fun ResultScreen(data: CeiData, onReset: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var isExporting by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Decoding a face-photo JPEG is small but non-trivial; keep it off the
        // composition/main thread rather than blocking the first frame.
        val bitmap by produceState<Bitmap?>(initialValue = null, data.faceImage) {
            value = withContext(Dispatchers.Default) {
                data.faceImage?.let { bytes -> BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
            }
        }

        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            val faceBitmap = bitmap
            if (faceBitmap != null) {
                androidx.compose.foundation.Image(
                    bitmap = faceBitmap.asImageBitmap(),
                    contentDescription = "Fotografie titular",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = listOfNotNull(data.firstName, data.lastName).joinToString(" ").ifBlank { "Titular necunoscut" },
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(24.dp))

        SectionCard(title = "Identitate") {
            InfoRow("CNP", data.cnp, monospace = true)
            InfoRow("Sex", data.gender)
            InfoRow("Cetățenie", data.citizenship)
        }

        SectionCard(title = "Naștere") {
            InfoRow("Data nașterii", data.birthDate)
            InfoRow("Locul nașterii", data.placeOfBirth)
        }

        SectionCard(title = "Document") {
            InfoRow("Serie și număr", data.documentSerialNo, monospace = true)
            InfoRow("Autoritate emitentă", data.issuingAuthority)
            InfoRow("Data emiterii", data.issuingDate)
            InfoRow("Data expirării", data.expiryDate)
        }

        SectionCard(title = "Adresă") {
            InfoRow("Domiciliu", data.currentAddress)
            if (data.temporaryAddresses.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("Reședințe temporare", style = MaterialTheme.typography.labelLarge)
                data.temporaryAddresses.forEach { AddressPeriodRow(it) }
            }
            if (data.foreignAddresses.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("Adrese în străinătate", style = MaterialTheme.typography.labelLarge)
                data.foreignAddresses.forEach { AddressPeriodRow(it) }
            }
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = {
                if (isExporting) return@Button // avoid spawning parallel exports/chooser sheets on double-tap.
                isExporting = true
                scope.launch {
                    try {
                        val uri = withContext(Dispatchers.IO) { CeiPdfExporter.export(context, data) }
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/pdf"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Exportă PDF"))
                        snackbarHostState.showSnackbar("PDF pregătit pentru partajare.")
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        snackbarHostState.showSnackbar("Nu am putut exporta PDF-ul.")
                    } finally {
                        isExporting = false
                    }
                }
            },
            enabled = !isExporting,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = MaterialTheme.shapes.medium,
        ) {
            Icon(imageVector = Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Exportă PDF")
        }

        Spacer(Modifier.height(12.dp))

        OutlinedButton(
            onClick = onReset,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = MaterialTheme.shapes.medium,
        ) {
            Text("Citește din nou")
        }
    }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Small-caps-style gold label: the accent is deliberate here (a
            // section header) rather than spread across the whole card.
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.4.sp),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.tertiary,
            )
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String?, monospace: Boolean = false) {
    // A null/blank value means the EF didn't carry this field -- render nothing rather
    // than a labeled row with a "—" placeholder.
    if (value.isNullOrBlank()) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(min = 130.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontFamily = if (monospace) FontFamily.Monospace else null,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun AddressPeriodRow(period: AddressPeriod) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(period.address, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = "${period.startDate ?: "?"} – ${period.endDate ?: "prezent"}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------------------------------------------------------------------------
// Error screen
// ---------------------------------------------------------------------------

@Composable
private fun ErrorScreen(kind: ReadErrorKind, retriesLeft: Int?, onRetry: () -> Unit) {
    val message = when (kind) {
        ReadErrorKind.WRONG_CAN -> "CAN incorect. Verificați cele 6 cifre de pe card."
        ReadErrorKind.WRONG_PIN -> buildString {
            append("PIN incorect")
            if (retriesLeft != null) append(" — au mai rămas $retriesLeft încercări.")
        }
        ReadErrorKind.PIN_BLOCKED -> "PIN blocat. Cardul trebuie deblocat."
        ReadErrorKind.CARD_LOST -> "Cardul s-a mișcat în timpul citirii. Așezați cardul plat pe spatele telefonului și țineți-l nemișcat."
        ReadErrorKind.COMMUNICATION, ReadErrorKind.UNKNOWN -> "Eroare de comunicație. Reîncercați."
    }

    Box(
        modifier = Modifier.fillMaxSize().systemBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.errorContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(48.dp),
                )
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text = message,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(32.dp))

            Button(
                onClick = onRetry,
                modifier = Modifier
                    .widthIn(min = 200.dp)
                    .height(48.dp),
                shape = MaterialTheme.shapes.medium,
            ) {
                Text("Reîncearcă")
            }
        }
    }
}
