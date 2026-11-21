package com.rython.authenticator.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.rython.authenticator.R
import com.rython.authenticator.core.OtpUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ScanScreen(
    /** Keep scanning after a successful read (transfer mode with several QR codes). */
    continuous: Boolean = false,
    onCode: (String) -> Unit,
    onMessage: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var lastFailureAt by remember { mutableLongStateOf(0L) }
    var finished by remember { mutableStateOf(false) }
    var accepted by remember { mutableIntStateOf(0) }
    val seen = remember { mutableSetOf<String>() }
    var lastAcceptedAt by remember { mutableLongStateOf(0L) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { result ->
        granted = result
        if (!result) onMessage(context.getString(R.string.scan_permission_needed))
    }

    LaunchedEffect(Unit) {
        if (!granted) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    val providerRef = remember { mutableStateOf<ProcessCameraProvider?>(null) }

    DisposableEffect(granted) {
        onDispose { providerRef.value?.unbindAll() }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (granted) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

            LaunchedEffect(granted) {
                val provider = withContext(Dispatchers.IO) {
                    runCatching { ProcessCameraProvider.getInstance(context).get() }.getOrNull()
                }
                if (provider == null) {
                    onMessage(context.getString(R.string.scan_camera_unavailable))
                    return@LaunchedEffect
                }
                providerRef.value = provider
                val executor = ContextCompat.getMainExecutor(context)
                val scanner = BarcodeScanning.getClient(
                    BarcodeScannerOptions.Builder()
                        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                        .build()
                )

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(executor) { imageProxy ->
                    val mediaImage = imageProxy.image
                    if (finished || mediaImage == null) {
                        imageProxy.close()
                        return@setAnalyzer
                    }
                    val input = InputImage.fromMediaImage(
                        mediaImage,
                        imageProxy.imageInfo.rotationDegrees
                    )
                    scanner.process(input)
                        .addOnSuccessListener { codes ->
                            val raw = codes.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue
                            if (raw == null || finished) return@addOnSuccessListener
                            val now = System.currentTimeMillis()
                            // Ignore the QR that is still in front of the camera.
                            if (raw in seen || now - lastAcceptedAt < 1500) return@addOnSuccessListener
                            runCatching { OtpUri.parse(raw) }
                                .onSuccess {
                                    seen += raw
                                    lastAcceptedAt = now
                                    accepted += 1
                                    if (!continuous) finished = true
                                    onCode(raw)
                                }
                                .onFailure { error ->
                                    if (now - lastFailureAt > 2500) {
                                        lastFailureAt = now
                                        onMessage(context.getString(R.string.scan_unsupported))
                                    }
                                }
                        }
                        .addOnCompleteListener { imageProxy.close() }
                }

                runCatching {
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis
                    )
                }.onFailure {
                    onMessage(context.getString(R.string.scan_start_failed, it.message.orEmpty()))
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    stringResource(R.string.scan_permission_denied),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium
                )
                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    modifier = Modifier.padding(top = 16.dp)
                ) { Text(stringResource(R.string.scan_allow)) }
            }
        }

        if (granted) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(48.dp)
                    .fillMaxWidth()
                    .border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(24.dp))
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color(0x99000000))
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    if (continuous) stringResource(R.string.scan_hint_continuous, accepted)
                    else stringResource(R.string.scan_hint_single),
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        IconButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = stringResource(R.string.action_close),
                tint = Color.White
            )
        }
    }
}
