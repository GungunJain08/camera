package com.example.cameraapp

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.compose.ui.tooling.preview.Preview
import coil.compose.rememberAsyncImagePainter // thumbnail ke liye coil use hoga
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import coil.compose.AsyncImage
import android.content.Intent



import androidx.camera.core.Preview as CameraPreview

@Composable
fun permission() {
    val permission = listOf(android.Manifest.permission.CAMERA)
    val isGranted = remember { mutableStateOf(false) }

    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permission ->
            isGranted.value = permission[android.Manifest.permission.CAMERA] == true
        }
    )

    LaunchedEffect(Unit) {
        isGranted.value = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.CAMERA
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    if (isGranted.value) {
        cameraScreen(paddingValues = PaddingValues())
    } else {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = { launcher.launch(permission.toTypedArray()) }
            ) {
                Text(text = "Permission")
            }
        }
    }
}


@Composable
fun cameraScreen(paddingValues: PaddingValues = PaddingValues()) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }

    // Camera Selector ko remember krna hoga taki flip krte time update ho sake
    var cameraSelector by remember { mutableStateOf(CameraSelector.DEFAULT_BACK_CAMERA) }

    val preview = CameraPreview.Builder().build()
    val imageCapture = remember { ImageCapture.Builder().build() }

    // Last captured photo ka URI store karne ke liye
    var lastCapturedUri by remember { mutableStateOf<Uri?>(null) }

    // Camera Bind
    LaunchedEffect(cameraSelector) { // flip hote hi dobara bind ho
        val cameraProvider = context.getCameraProvider()
        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(
            lifecycleOwner,
            cameraSelector,
            preview,
            imageCapture
        )
        preview.setSurfaceProvider(previewView.surfaceProvider)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        contentAlignment = Alignment.BottomCenter
    ) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Gray.copy(alpha = 0.7f))
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {

            //  Left side: Flip Camera Button
            IconButton(
                onClick = {
                    // Flip between front and back
                    cameraSelector =
                        if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA)
                            CameraSelector.DEFAULT_FRONT_CAMERA
                        else
                            CameraSelector.DEFAULT_BACK_CAMERA
                },
                modifier = Modifier
                    .size(50.dp)
                    .background(Color.White, CircleShape)
            ) {
                Text("🔄")
            }

            //  Center: Capture Button
            IconButton(
                onClick = {
                    capturePhoto(imageCapture, context) { uri ->
                        lastCapturedUri = uri //  captured hone ke baad thumbnail update
                    }
                },
                modifier = Modifier
                    .size(60.dp)
                    .background(Color.White, CircleShape)
                    .padding(8.dp)
                    .background(Color.Red, CircleShape)
            ) {}

            // Right side: Thumbnail of last captured image
            if (lastCapturedUri != null) {
                Image(
                    painter = rememberAsyncImagePainter(lastCapturedUri),
                    contentDescription = "Last Captured",
                    modifier = Modifier
                        .size(50.dp)
                        .background(Color.Black, CircleShape)
                        .clickable {
                            lastCapturedUri?.let { uri ->
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri, "image/*")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                try {
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(
                                        context,
                                        "No Gallery App Found",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                )
            }
        }
    }
}


@Preview(showBackground = true, showSystemUi = true)
@Composable
fun PreviewCameraScreen() {
    cameraScreen()
}

private suspend fun Context.getCameraProvider(): ProcessCameraProvider =
    suspendCoroutine { continuation ->
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            continuation.resume(cameraProviderFuture.get())
        }, ContextCompat.getMainExecutor(this))
    }

// capture function modified taki URI return kare
private fun capturePhoto(imageCapture: ImageCapture, context: Context, onPhotoSaved: (Uri) -> Unit) {
    val name = "Mycamera_${System.currentTimeMillis()}.jpg"
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MyCamera-Image")
    }
    val outputOptions = ImageCapture.OutputFileOptions.Builder(
        context.contentResolver,
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        contentValues
    ).build()

    imageCapture.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                Toast.makeText(context, "Image Saved", Toast.LENGTH_SHORT).show()
                outputFileResults.savedUri?.let { onPhotoSaved(it) } //  thumbnail update karega
            }

            override fun onError(exception: ImageCaptureException) {
                Toast.makeText(context, "Failed: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
        }
    )
}
