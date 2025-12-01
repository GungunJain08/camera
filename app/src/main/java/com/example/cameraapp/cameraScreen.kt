package com.example.cameraapp

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.location.Location
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.rememberAsyncImagePainter
import com.example.cameraapp.data.AppDatabase
import com.example.cameraapp.data.Photo
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine


import androidx.camera.core.Preview as CameraPreview

@Composable
fun permission() {
    val permissions = listOf(
        android.Manifest.permission.CAMERA,
        android.Manifest.permission.ACCESS_FINE_LOCATION
    )
    val allPermissionsGranted = remember { mutableStateOf(false) }

    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissionsMap ->
            allPermissionsGranted.value = permissionsMap.values.all { it }
        }
    )

    LaunchedEffect(Unit) {
        allPermissionsGranted.value = permissions.all {
            ContextCompat.checkSelfPermission(context, it) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    if (allPermissionsGranted.value) {
        cameraScreen(paddingValues = PaddingValues())
    } else {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = { launcher.launch(permissions.toTypedArray()) }
            ) {
                Text(text = "Request Permissions")
            }
        }
    }
}


@Composable
fun cameraScreen(paddingValues: PaddingValues = PaddingValues()) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }

    // --- DATABASE SETUP ---
    val scope = rememberCoroutineScope()
    val photoDao = AppDatabase.getDatabase(context).photoDao()
    // --- END DATABASE SETUP ---

    var cameraSelector by remember { mutableStateOf(CameraSelector.DEFAULT_BACK_CAMERA) }
    val preview = CameraPreview.Builder().build()
    val imageCapture = remember { ImageCapture.Builder().build() }
    var lastCapturedUri by remember { mutableStateOf<Uri?>(null) }

    LaunchedEffect(cameraSelector) {
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

            IconButton(
                onClick = {
                    cameraSelector =
                        if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA)
                            CameraSelector.DEFAULT_FRONT_CAMERA
                        else
                            CameraSelector.DEFAULT_BACK_CAMERA
                },
                modifier = Modifier.size(50.dp).background(Color.White, CircleShape)
            ) {
                Text("🔄")
            }

            IconButton(
                onClick = {
                    getCurrentLocation(context) { location ->
                        if (location == null) {
                            Toast.makeText(context, "Location not available. Cannot save to DB.", Toast.LENGTH_SHORT).show()
                            return@getCurrentLocation
                        }

                        capturePhoto(imageCapture, context, location) { uri ->
                            lastCapturedUri = uri // Update UI first

                            // --- SAVE TO DATABASE ---
                            scope.launch(Dispatchers.IO) { // Use IO thread for DB operations
                                val newPhoto = Photo(
                                    imageUri = uri.toString(),
                                    latitude = location.latitude,
                                    longitude = location.longitude,
                                    timestamp = System.currentTimeMillis()
                                )
                                photoDao.insert(newPhoto)

                                // Show a toast on the main thread after saving
                                withContext(Dispatchers.Main){
                                    Toast.makeText(context, "Saved to local database!", Toast.LENGTH_SHORT).show()
                                }
                            }
                            // --- END SAVE TO DATABASE ---
                        }
                    }
                },
                modifier = Modifier.size(60.dp).background(Color.White, CircleShape).padding(8.dp).background(Color.Red, CircleShape)
            ) {}

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
                                    Toast.makeText(context, "No Gallery App Found", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                )
            } else {
                // Placeholder for the thumbnail
                Spacer(modifier = Modifier.size(50.dp))
            }
        }
    }
}

@SuppressLint("MissingPermission")
private fun getCurrentLocation(context: Context, onResult: (Location?) -> Unit) {
    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
        .addOnSuccessListener { location: Location? ->
            if (location == null) {
                Toast.makeText(context, "Cannot get current location.", Toast.LENGTH_SHORT).show()
            }
            onResult(location)
        }
        .addOnFailureListener { e ->
            Toast.makeText(context, "Failed to get location: ${e.message}", Toast.LENGTH_SHORT).show()
            onResult(null)
        }
}

private fun capturePhoto(
    imageCapture: ImageCapture,
    context: Context,
    location: Location,
    onPhotoSaved: (Uri) -> Unit
) {
    val name = "Mycamera_${System.currentTimeMillis()}.jpg"
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MyCamera-Image")
    }

    val metadata = ImageCapture.Metadata().apply {
        this.location = location
    }

    val outputOptions = ImageCapture.OutputFileOptions.Builder(
        context.contentResolver,
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        contentValues
    ).setMetadata(metadata).build()

    imageCapture.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                outputFileResults.savedUri?.let { onPhotoSaved(it) }
            }

            override fun onError(exception: ImageCaptureException) {
                Toast.makeText(context, "Failed: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
        }
    )
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
