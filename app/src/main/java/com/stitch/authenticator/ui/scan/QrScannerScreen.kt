package com.stitch.authenticator.ui.scan

import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview as CameraPreview
import androidx.camera.core.TorchState
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.stitch.authenticator.passkeys.PasskeyQr
import com.stitch.authenticator.theme.StitchAuthenticatorTheme
import com.stitch.authenticator.theme.StitchMotion
import com.stitch.authenticator.theme.bouncySpring
import kotlinx.coroutines.launch

private const val OtpAuthPrefix = "otpauth://"

/** What a decoded payload *is* — the routing decision [analyzeFrame] hands back to the screen. */
private enum class ScannedCodeKind {
  /** An authenticator setup code: pushes the add form with the code, exactly as always. */
  OtpAuth,

  /**
   * A passkey-shaped payload (`FIDO:/`, `webauthn://`, WebAuthn request JSON — see [PasskeyQr]):
   * pushes the save sheet, never the "invalid code" snackbar.
   */
  Passkey,

  /** Anything else: snacked and ignored, the camera keeps running. */
  Other,
}

private fun classifyScannedCode(value: String): ScannedCodeKind =
  when {
    value.startsWith(OtpAuthPrefix, ignoreCase = true) -> ScannedCodeKind.OtpAuth
    PasskeyQr.parseOrNull(value) != null -> ScannedCodeKind.Passkey
    else -> ScannedCodeKind.Other
  }

/** What the scanner screen shows for the current camera-permission answer. */
internal enum class ScannerStage {
  /** Permission not answered yet — the system prompt is (or is about to be) in front. */
  AwaitingPermission,

  /** Refused: the rationale, whose "Enter key instead" is the way out that needs no camera. */
  Rationale,

  /** Granted: the live viewfinder. */
  Viewport,
}

/**
 * The screen's whole permission story in one place, so the order is visible — and testable.
 * Granted wins even before the answer is recorded, and the rationale appears only once the
 * prompt has actually been answered, never while the system dialog is still in front.
 */
internal fun scannerStage(granted: Boolean, asked: Boolean): ScannerStage =
  when {
    granted -> ScannerStage.Viewport
    !asked -> ScannerStage.AwaitingPermission
    else -> ScannerStage.Rationale
  }

/** How long the same code is left alone after being reported — see [ScanCooldown]. */
private const val SameCodeCooldownMillis = 1_500L

/**
 * Scanning setup QR codes with the camera.
 *
 * Two promises hold the whole screen together:
 *
 * 1. **On-device only.** CameraX streams frames to ML Kit's *bundled* barcode model, which runs in
 *    this app's process — frames are never saved and never uploaded, and the model needs no Play
 *    Services. Only QR codes are looked for ([Barcode.FORMAT_QR_CODE]), so nothing else in frame
 *    is even decoded.
 * 2. **A scan never stores anything by itself.** An `otpauth://` code pops this screen and pushes
 *    the normal add form (Link mode) with the code in the box, so the same draft, validation and
 *    save pipeline as a paste stands between the camera and a stored key. A passkey-shaped payload
 *    — a `FIDO:/` handle, a `webauthn://` link, a WebAuthn request JSON, classified by
 *    [PasskeyQr] — pops this screen and pushes the passkey save sheet, which still shows what was
 *    read and asks before anything is stored. Anything else gets a snackbar and is ignored — the
 *    camera keeps running.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScannerScreen(
  onBackClick: () -> Unit,
  onSetupLinkScanned: (String) -> Unit,
  onPasskeyScanned: (String) -> Unit,
  onEnterKeyInstead: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  var granted by
    remember {
      mutableStateOf(
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
          PackageManager.PERMISSION_GRANTED
      )
    }
  // Whether the system prompt has been answered at least once on this visit: it separates "the
  // prompt is about to appear" from "the prompt was answered with no", which look identical
  // otherwise.
  var asked by remember { mutableStateOf(granted) }

  val permissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
      granted = isGranted
      asked = true
    }

  // Ask once on entry; every later attempt comes from a tap on the rationale, never from a loop
  // that would re-prompt after a refusal.
  LaunchedEffect(Unit) {
    if (!granted) permissionLauncher.launch(Manifest.permission.CAMERA)
  }

  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()

  // The analyzer runs ~30x a second: the first accepted code must navigate exactly once, or a
  // frame arriving while the first transition is still in flight would pop the form it just
  // pushed. [ScanCooldown] narrows the window further; this closes it.
  var handled by remember { mutableStateOf(false) }
  val acceptScan: (String) -> Unit = { link ->
    if (!handled) {
      handled = true
      onSetupLinkScanned(link)
    }
  }
  // Same one-shot guard as an OTP link: a passkey payload also ends this screen (it pushes the
  // save sheet), and the cooldown must not fire a second navigation while the pop animates.
  val acceptPasskeyScan: (String) -> Unit = { payload ->
    if (!handled) {
      handled = true
      onPasskeyScanned(payload)
    }
  }
  val rejectScan: () -> Unit = {
    scope.launch {
      // One message at a time: a code held in frame refreshes the message rather than stacking
      // copies of it.
      if (snackbarHostState.currentSnackbarData == null) {
        snackbarHostState.showSnackbar("That QR code isn't an authenticator setup code")
      }
    }
  }

  // The camera bound inside the preview, surfaced so the toggle in the top bar can drive it. It
  // lives here rather than in the viewport because the button is chrome, not preview.
  var camera by remember { mutableStateOf<Camera?>(null) }
  var torchOn by remember { mutableStateOf(false) }
  val lifecycleOwner = LocalLifecycleOwner.current

  // The torch state is the camera's own, not the button's: the flash can change underneath us
  // (battery saver drops it), and the icon must not lie about that.
  LaunchedEffect(camera) {
    camera?.cameraInfo?.torchState?.observe(lifecycleOwner) { state ->
      torchOn = state == TorchState.ON
    }
  }

  Scaffold(
    modifier = modifier,
    topBar = {
      TopAppBar(
        title = { Text("Scan QR code") },
        navigationIcon = {
          IconButton(onClick = onBackClick) {
            Icon(imageVector = Icons.Filled.Close, contentDescription = "Close")
          }
        },
        actions = {
          // Fades in only once a camera actually exists: before that there is nothing to light.
          AnimatedVisibility(
            visible = granted && camera != null,
            enter = fadeIn(animationSpec = StitchMotion.Smooth),
            exit = fadeOut(animationSpec = StitchMotion.Smooth),
          ) {
            IconButton(onClick = { camera?.cameraControl?.enableTorch(!torchOn) }) {
              Icon(
                imageVector = TorchIcon,
                contentDescription =
                  if (torchOn) "Turn off the flashlight" else "Turn on the flashlight",
                tint =
                  if (torchOn) MaterialTheme.colorScheme.primary
                  else MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        },
      )
    },
    snackbarHost = { SnackbarHost(snackbarHostState) },
  ) { innerPadding ->
    Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
      when (scannerStage(granted = granted, asked = asked)) {
        ScannerStage.Viewport ->
          ScannerViewport(
            onSetupLinkScanned = acceptScan,
            onPasskeyScanned = acceptPasskeyScan,
            onOtherCodeScanned = rejectScan,
            onCameraReady = { camera = it },
            modifier = Modifier.fillMaxSize(),
          )

        ScannerStage.AwaitingPermission ->
          Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
          }

        ScannerStage.Rationale ->
          CameraRationale(
            onRetry = { permissionLauncher.launch(Manifest.permission.CAMERA) },
            onEnterKeyInstead = onEnterKeyInstead,
            modifier = Modifier.fillMaxSize(),
          )
      }
    }
  }
}

/** What the dispose path needs: the provider to unbind from. Never composed from, so not state. */
private class PreviewBinding(var provider: ProcessCameraProvider? = null)

/**
 * The live preview, the viewfinder chrome over it, and the analysis pipeline behind both.
 *
 * Preview and [ImageAnalysis] share one bind on the entry's lifecycle.
 * [ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST] drops *every* frame but the newest, so a slow read can
 * never back the pipeline up; the single-thread executor does the byte shuffling; ML Kit's task
 * listeners — which default to the main thread — deliver results to [ScanCooldown] and the
 * callbacks, which is where navigation happens.
 */
@Composable
private fun ScannerViewport(
  onSetupLinkScanned: (String) -> Unit,
  onPasskeyScanned: (String) -> Unit,
  onOtherCodeScanned: () -> Unit,
  onCameraReady: (Camera) -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current

  // The view factory runs once; it must call whichever callbacks the composition currently holds,
  // not the ones it happened to be created with.
  val latestOnLink = rememberUpdatedState(onSetupLinkScanned)
  val latestOnPasskey = rememberUpdatedState(onPasskeyScanned)
  val latestOnOther = rememberUpdatedState(onOtherCodeScanned)
  val latestOnReady = rememberUpdatedState(onCameraReady)

  val scanner =
    remember {
      BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
      )
    }
  val cooldown = remember { ScanCooldown() }
  val binding = remember { PreviewBinding() }

  Box(modifier = modifier) {
    AndroidView(
      modifier = Modifier.fillMaxSize(),
      factory = { viewContext ->
        PreviewView(viewContext).apply {
          scaleType = PreviewView.ScaleType.FILL_CENTER
          val providerFuture = ProcessCameraProvider.getInstance(viewContext)
          providerFuture.addListener(
            {
              val cameraProvider = providerFuture.get()
              val cameraPreview = CameraPreview.Builder().build().also { cameraPreview ->
                cameraPreview.setSurfaceProvider(surfaceProvider)
              }
              val analysis =
                ImageAnalysis.Builder()
                  .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                  .build()
              analysis.setAnalyzer(ContextCompat.getMainExecutor(viewContext)) { imageProxy ->
                analyzeFrame(imageProxy, scanner, cooldown) { value, kind ->
                  when (kind) {
                    ScannedCodeKind.OtpAuth -> latestOnLink.value(value)
                    ScannedCodeKind.Passkey -> latestOnPasskey.value(value)
                    ScannedCodeKind.Other -> latestOnOther.value()
                  }
                }
              }

              // Foldables report the back camera as unavailable while their inner screen is up;
              // the front one still scans, so the selector bends before anything throws.
              val selector =
                try {
                  if (cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                    CameraSelector.DEFAULT_BACK_CAMERA
                  } else {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                  }
                } catch (ignored: Exception) {
                  CameraSelector.DEFAULT_FRONT_CAMERA
                }

              cameraProvider.unbindAll()
              binding.provider = cameraProvider
              latestOnReady.value(
                cameraProvider.bindToLifecycle(lifecycleOwner, selector, cameraPreview, analysis)
              )
            },
            ContextCompat.getMainExecutor(viewContext),
          )
        }
      },
    )

    // The camera's own chrome enters on springs, born-hidden so the entrance actually plays.
    val entered = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(
      visibleState = entered,
      enter =
        fadeIn(animationSpec = StitchMotion.Smooth) +
          slideInVertically(animationSpec = bouncySpring()) { fullHeight -> fullHeight / 6 },
      exit = fadeOut(animationSpec = StitchMotion.Smooth),
    ) {
      Box(Modifier.fillMaxSize()) {
        Viewfinder(modifier = Modifier.fillMaxSize())
        Text(
          text = "Line the code up inside the frame",
          style = MaterialTheme.typography.bodyMedium,
          color = Color.White,
          textAlign = TextAlign.Center,
          modifier =
            Modifier.align(Alignment.BottomCenter)
              .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
        )
      }
    }
  }

  DisposableEffect(Unit) {
    onDispose {
      // Frames stop here: the camera releases as the screen leaves, and the model is closed with
      // it rather than waiting for GC.
      binding.provider?.unbindAll()
      scanner.close()
    }
  }
}

/**
 * One frame in, at most one report out.
 *
 * ML Kit's task listeners run on the main thread by default, which is where [report] decides —
 * so the cooldown and any navigation happen without hopping dispatchers. The proxy is closed
 * exactly once, in the complete listener, so the analysis slot frees whether the read succeeded
 * or not.
 */
private fun analyzeFrame(
  imageProxy: ImageProxy,
  scanner: BarcodeScanner,
  cooldown: ScanCooldown,
  report: (value: String, kind: ScannedCodeKind) -> Unit,
) {
  val mediaImage = imageProxy.image
  if (mediaImage == null) {
    imageProxy.close()
    return
  }

  val input = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
  scanner
    .process(input)
    .addOnSuccessListener { barcodes ->
      val value =
        barcodes.firstNotNullOfOrNull { barcode -> barcode.rawValue }
          ?: return@addOnSuccessListener
      if (cooldown.shouldReport(value)) {
        report(value, classifyScannedCode(value))
      }
    }
    .addOnCompleteListener { imageProxy.close() }
}

/**
 * One report per distinct code per [SameCodeCooldownMillis].
 *
 * The camera re-reads the same code ~30 times a second: without this, an accepted code would
 * navigate twice and a foreign code held in frame would fire a snackbar storm. Touched only from
 * ML Kit's main-thread listeners, so plain fields are enough.
 */
private class ScanCooldown(private val windowMillis: Long = SameCodeCooldownMillis) {
  private var lastValue: String? = null
  private var lastReportedAt = 0L

  /** Whether [value] has been quiet long enough to be worth reporting again. */
  fun shouldReport(value: String): Boolean {
    val now = SystemClock.elapsedRealtime()
    if (value == lastValue && now - lastReportedAt < windowMillis) return false
    lastValue = value
    lastReportedAt = now
    return true
  }
}

/**
 * Shown when the camera was refused: what the camera is for, that nothing leaves the device, and a
 * way out that does not need a camera at all.
 */
@Composable
private fun CameraRationale(
  onRetry: () -> Unit,
  onEnterKeyInstead: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val entered = remember { MutableTransitionState(false).apply { targetState = true } }

  AnimatedVisibility(
    visibleState = entered,
    modifier = modifier,
    enter =
      fadeIn(animationSpec = StitchMotion.Smooth) +
        slideInVertically(animationSpec = bouncySpring()) { fullHeight -> fullHeight / 4 },
    exit = fadeOut(animationSpec = StitchMotion.Smooth),
  ) {
    Column(
      modifier = Modifier.fillMaxSize().padding(32.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      Text(
        text = "Camera access is needed to scan",
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center,
      )
      Spacer(Modifier.height(8.dp))
      Text(
        text =
          "Scanning reads QR codes from the live camera right on this device — frames are never " +
            "saved or sent anywhere. You can also type the setup key instead.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
      )
      Spacer(Modifier.height(24.dp))
      Button(onClick = onRetry) { Text("Allow camera access") }
      TextButton(onClick = onEnterKeyInstead) { Text("Enter key instead") }
    }
  }
}

/**
 * The dim frame: everything outside the window falls back and four brackets mark where the code
 * belongs. The camera still analyzes the *whole* frame — this is guidance for the wrist, not a
 * crop.
 */
@Composable
private fun Viewfinder(modifier: Modifier = Modifier) {
  val dimColor = Color.Black.copy(alpha = 0.55f)
  val frameColor = Color.White.copy(alpha = 0.9f)

  Canvas(modifier = modifier) {
    // The window is square — QR codes are — and as wide as the short side allows, minus margins.
    val side = minOf(size.width, size.height) * 0.78f
    val left = (size.width - side) / 2f
    val top = (size.height - side) / 2f

    // Four bands of dim around the window: one rect each, rather than one path with a hole that
    // would need even-odd fill and says no more.
    drawRect(color = dimColor, topLeft = Offset(0f, 0f), size = Size(size.width, top))
    drawRect(
      color = dimColor,
      topLeft = Offset(0f, top + side),
      size = Size(size.width, size.height - top - side),
    )
    drawRect(color = dimColor, topLeft = Offset(0f, top), size = Size(left, side))
    drawRect(
      color = dimColor,
      topLeft = Offset(left + side, top),
      size = Size(size.width - left - side, side),
    )

    // Corner brackets — the same frame the scan glyph in the speed dial draws.
    val bracket = side * 0.14f
    val frame =
      Path().apply {
        moveTo(left, top + bracket)
        lineTo(left, top)
        lineTo(left + bracket, top)
        moveTo(left + side - bracket, top)
        lineTo(left + side, top)
        lineTo(left + side, top + bracket)
        moveTo(left + side, top + side - bracket)
        lineTo(left + side, top + side)
        lineTo(left + side - bracket, top + side)
        moveTo(left + bracket, top + side)
        lineTo(left, top + side)
        lineTo(left, top + side - bracket)
      }
    drawPath(
      path = frame,
      color = frameColor,
      style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
    )
  }
}

/** The bolt from Material's flash icon, drawn here: the core icon set has no torch glyph. */
private val TorchIcon: ImageVector by lazy {
  ImageVector.Builder(
    name = "Torch",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
  ).apply {
    path(fill = SolidColor(Color.Black)) {
      // One zig-zag: down the left stem, out to the bottom tip, back up to the top corner.
      moveTo(7f, 2f)
      lineTo(7f, 13f)
      lineTo(10f, 13f)
      lineTo(10f, 22f)
      lineTo(17f, 10f)
      lineTo(13f, 10f)
      lineTo(17f, 2f)
      close()
    }
  }.build()
}

@Preview(name = "Camera rationale", showBackground = true)
@Composable
private fun CameraRationalePreview() {
  StitchAuthenticatorTheme {
    CameraRationale(onRetry = {}, onEnterKeyInstead = {})
  }
}
