package org.openflux.app.ui.profiles

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Size
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import kotlinx.coroutines.launch
import org.openflux.app.LocalOpenFluxApp
import org.openflux.app.R
import org.openflux.app.data.ProfileDeepLink

/**
 * Full-screen QR scanner for the profiles tab. Decodes an openflux://import
 * deep link (see ProfileDeepLink.kt) and saves the resulting profile
 * immediately - the same payload a shared-link QR code carries. Keeps
 * scanning if the code isn't a valid profile link; shows one "not valid"
 * toast per distinct code, not per camera frame.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScanScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val app = LocalOpenFluxApp.current
    val scope = rememberCoroutineScope()
    val permissionGranted = remember {
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }
    var permissionAsked by remember { mutableStateOf(false) }
    var profileAdded by remember { mutableStateOf(false) }
    var lastScanError by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionAsked = true
        if (!granted) {
            Toast.makeText(context, context.getString(R.string.qr_scan_permission_denied), Toast.LENGTH_LONG)
                .show()
            onDone()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.qr_scan_title)) },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.qr_scan_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (permissionGranted || permissionAsked) {
            Box(Modifier.fillMaxSize().padding(padding)) {
                QrCameraPreview(
                    onQrText = { text ->
                        val profile = ProfileDeepLink.parse(Uri.parse(text))
                        if (profile == null) {
                            if (lastScanError != text) {
                                lastScanError = text
                                Toast.makeText(context, context.getString(R.string.qr_scan_invalid), Toast.LENGTH_SHORT)
                                    .show()
                            }
                        } else if (!profileAdded) {
                            profileAdded = true
                            scope.launch {
                                app.profileRepository.save(profile)
                                Toast.makeText(context, context.getString(R.string.qr_scan_added), Toast.LENGTH_SHORT)
                                    .show()
                                onDone()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                Surface(
                    color = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp),
                ) {
                    Text(
                        stringResource(R.string.qr_scan_hint),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            ) {
                Text(
                    stringResource(R.string.qr_scan_permission_message),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text(stringResource(R.string.qr_scan_permission_button))
                }
            }
        }
    }
}

/**
 * Live camera preview + ML Kit QR analysis. Binds to the host LifecycleOwner
 * for the screen's lifetime and unbinds (freeing the camera) on dispose.
 * Detection callbacks arrive on the main thread (ML Kit's default), which is
 * why the lambda can safely touch Compose state.
 */
@Composable
private fun QrCameraPreview(onQrText: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val scanner = remember { BarcodeScanning.getClient() }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val onQrTextRef = rememberUpdatedState(onQrText)

    DisposableEffect(lifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val bindCamera = Runnable {
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }

            val analysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(1280, 720))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(analysisExecutor) { imageProxy: ImageProxy ->
                val mediaImage = imageProxy.image
                if (mediaImage == null) {
                    imageProxy.close()
                    return@setAnalyzer
                }
                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                scanner.process(image)
                    .addOnSuccessListener { barcodes ->
                        val raw = barcodes.firstOrNull()?.rawValue
                        if (raw != null) onQrTextRef.value(raw)
                    }
                    .addOnCompleteListener { imageProxy.close() }
            }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            } catch (e: Exception) {
                // No back camera (or binding failed) - the scan screen just
                // shows the black preview; the user can back out.
            }
        }
        cameraProviderFuture.addListener(bindCamera, ContextCompat.getMainExecutor(context))

        onDispose {
            cameraProviderFuture.addListener(
                { runCatching { cameraProviderFuture.get().unbindAll() } },
                ContextCompat.getMainExecutor(context),
            )
            scanner.close()
            analysisExecutor.shutdown()
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}