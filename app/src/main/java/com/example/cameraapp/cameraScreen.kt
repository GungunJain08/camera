package com.example.cameraapp

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.rememberAsyncImagePainter
import com.amplifyframework.core.Amplify
import com.amplifyframework.storage.StorageAccessLevel
import com.amplifyframework.storage.options.StorageUploadFileOptions
import com.example.cameraapp.data.AppDatabase
import com.example.cameraapp.data.Photo
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine


import androidx.camera.core.Preview as CameraPreview

@Composable
fun permission() {
    val context = LocalContext.current
    val permissions = listOf(android.Manifest.permission.CAMERA, android.Manifest.permission.ACCESS_FINE_LOCATION)
    var allPermissionsGranted by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(contract = ActivityResultContracts.RequestMultiplePermissions()) { permissionsMap ->
        allPermissionsGranted = permissionsMap.values.all { it }
    }

    LaunchedEffect(Unit) {
        val permissionsToCheck = permissions.all {
            ContextCompat.checkSelfPermission(context, it) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (permissionsToCheck) {
            allPermissionsGranted = true
        } else {
            launcher.launch(permissions.toTypedArray()) // Automatically request permissions on startup
        }
    }

    if (allPermissionsGranted) {
        cameraScreen(paddingValues = PaddingValues())
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Requesting permissions...")
        }
    }
}

@Composable
fun cameraScreen(paddingValues: PaddingValues = PaddingValues()) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val scope = rememberCoroutineScope()
    val photoDao = AppDatabase.getDatabase(context).photoDao()
    var cameraSelector by remember { mutableStateOf(CameraSelector.DEFAULT_BACK_CAMERA) }
    val imageCapture = remember { ImageCapture.Builder().build() }
    var lastCapturedUri by remember { mutableStateOf<android.net.Uri?>(null) }

    // --- UI POLISH SETUP ---
    val haptics = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.9f else 1f)
    // --- END UI POLISH SETUP ---

    LaunchedEffect(cameraSelector) {
        val cameraProvider = context.getCameraProvider()
        val preview = CameraPreview.Builder().build()
        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageCapture)
        preview.setSurfaceProvider(previewView.surfaceProvider)
    }

    Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.BottomCenter) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)))) // 1. GRADIENT
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween, 
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress) // 2. HAPTIC FEEDBACK
                cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
            }, modifier = Modifier.size(50.dp).background(Color.White, CircleShape)) {
                Text("🔄", fontSize = 24.sp)
            }

            IconButton(onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress) // 2. HAPTIC FEEDBACK
                scope.launch {
                    Toast.makeText(context, "Fetching location...", Toast.LENGTH_SHORT).show()
                    getCurrentLocation(context) { location ->
                        if (location == null) {
                            (context as? Activity)?.runOnUiThread { Toast.makeText(context, "Location not available.", Toast.LENGTH_SHORT).show() }
                            return@getCurrentLocation
                        }
                        capturePhotoToMemory(imageCapture, context) { bitmap ->
                            if (bitmap != null) {
                                scope.launch(Dispatchers.IO) {
                                    val savedUri = addWatermarkAndSave(context, bitmap, location)
                                    if (savedUri != null) {
                                        withContext(Dispatchers.Main) {
                                            lastCapturedUri = savedUri // Update thumbnail
                                        }
                                        val newPhoto = Photo(imageUri = savedUri.toString(), latitude = location.latitude, longitude = location.longitude, timestamp = System.currentTimeMillis())
                                        photoDao.insert(newPhoto)
                                        withContext(Dispatchers.Main) { Toast.makeText(context, "Saved to local DB!", Toast.LENGTH_SHORT).show() }
                                        val photoKey = "photo_${System.currentTimeMillis()}.jpg"
                                        val fileToUpload = savedUri.toFile(context)
                                        if (fileToUpload != null) {
                                            uploadToS3(fileToUpload, photoKey)
                                        } else {
                                            Log.e("S3Upload", "Could not convert Uri to File for S3 upload.")
                                        }
                                    }
                                }
                            } else {
                                (context as? Activity)?.runOnUiThread { Toast.makeText(context, "Failed to capture photo.", Toast.LENGTH_SHORT).show() }
                            }
                        }
                    }
                }
            },
            interactionSource = interactionSource,
            modifier = Modifier.scale(scale).size(70.dp).background(Color.White, CircleShape).padding(8.dp).background(Color.Red, CircleShape)) {}

            if (lastCapturedUri != null) {
                Image(
                    painter = rememberAsyncImagePainter(lastCapturedUri),
                    contentDescription = "Last Captured",
                    modifier = Modifier.size(50.dp).background(Color.Black, CircleShape).clickable { 
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress) // 2. HAPTIC FEEDBACK
                        lastCapturedUri?.let { uri ->
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, "image/*")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            try { context.startActivity(intent) } catch (e: Exception) { Toast.makeText(context, "No Gallery App Found", Toast.LENGTH_SHORT).show() }
                        }
                    }
                )
            } else {
                Spacer(modifier = Modifier.size(50.dp))
            }
        }
    }
}

private fun uploadToS3(file: File, key: String) {
    val options = StorageUploadFileOptions.builder().accessLevel(StorageAccessLevel.PUBLIC).build()
    Amplify.Storage.uploadFile(key, file, options, {
        result -> Log.i("MyAmplifyApp", "Successfully uploaded: ${result.key}")
        file.delete() // Clean up temp file
    }, {
        error -> Log.e("MyAmplifyApp", "Upload failed", error)
    })
}

private fun android.net.Uri.toFile(context: Context): File? {
    val contentResolver = context.contentResolver
    val file = File(context.cacheDir, "temp_${System.currentTimeMillis()}")
    try {
        contentResolver.openInputStream(this)?.use { input ->
            FileOutputStream(file).use { output ->
                input.copyTo(output)
            }
        }
    } catch (e: Exception) {
        Log.e("UriToFile", "Failed to convert Uri to File", e)
        return null
    }
    return file
}

private fun capturePhotoToMemory(imageCapture: ImageCapture, context: Context, onPhotoCaptured: (Bitmap?) -> Unit) {
    imageCapture.takePicture(ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageCapturedCallback() {
        override fun onCaptureSuccess(image: ImageProxy) {
            onPhotoCaptured(image.toBitmap().rotate(image.imageInfo.rotationDegrees.toFloat()))
            image.close()
        }
        override fun onError(exception: ImageCaptureException) {
            Log.e("CameraCapture", "Capture failed: ${exception.message}", exception)
            onPhotoCaptured(null)
        }
    })
}

@SuppressLint("MissingPermission")
private fun getCurrentLocation(context: Context, onResult: (Location?) -> Unit) {
    LocationServices.getFusedLocationProviderClient(context).getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
        .addOnSuccessListener { location: Location? -> onResult(location) }
        .addOnFailureListener { onResult(null) }
}

private fun addWatermarkAndSave(context: Context, originalBitmap: Bitmap, location: Location): android.net.Uri? {
    (context as? Activity)?.runOnUiThread { Toast.makeText(context, "Watermarking...", Toast.LENGTH_SHORT).show() }
    val mutableBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = Canvas(mutableBitmap)
    val paint = Paint().apply { color = android.graphics.Color.WHITE; textSize = 40f; isAntiAlias = true }
    val geocoder = Geocoder(context, Locale.getDefault())
    val addresses = try { geocoder.getFromLocation(location.latitude, location.longitude, 1) } catch (e: Exception) { null }

    val address = addresses?.firstOrNull()
    val addressLine = address?.getAddressLine(0) ?: "Unknown Location"
    (context as? Activity)?.runOnUiThread { Toast.makeText(context, "Address: $addressLine", Toast.LENGTH_SHORT).show() }

    val latLong = "Lat ${String.format("%.5f", location.latitude)}, Long ${String.format("%.5f", location.longitude)}"
    val timestamp = SimpleDateFormat("dd/MM/yyyy hh:mm:ss a", Locale.getDefault()).format(Date())

    val bgPaint = Paint().apply { color = android.graphics.Color.argb(128, 0, 0, 0) }
    val textBounds = Rect()
    paint.getTextBounds(addressLine, 0, addressLine.length, textBounds)
    val textHeight = textBounds.height()
    val padding = 20f
    val rectBottom = canvas.height - 100f
    val rectTop = rectBottom - (textHeight * 4) - (padding * 4)
    canvas.drawRect(RectF(padding, rectTop, canvas.width - padding, rectBottom), bgPaint)

    var yPos = rectTop + textHeight + padding
    canvas.drawText(addressLine, padding * 2, yPos, paint)
    yPos += textHeight + padding
    canvas.drawText(latLong, padding * 2, yPos, paint)
    yPos += textHeight + padding
    canvas.drawText(timestamp, padding * 2, yPos, paint)

    val name = "Watermarked_${System.currentTimeMillis()}.jpg"
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MyCameraApp") }
    }

    val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
    uri?.let {
        try {
            context.contentResolver.openOutputStream(it)?.use { stream ->
                mutableBitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
                (context as? Activity)?.runOnUiThread { Toast.makeText(context, "Photo saved to gallery!", Toast.LENGTH_SHORT).show() }
            }
        } catch(e: Exception) {
             (context as? Activity)?.runOnUiThread { Toast.makeText(context, "Failed to save photo.", Toast.LENGTH_SHORT).show() }
             return null
        }
    }
    return uri
}

fun ImageProxy.toBitmap(): Bitmap {
    val buffer = planes[0].buffer.apply { rewind() }
    val bytes = ByteArray(buffer.capacity()).apply { buffer.get(this) }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}

fun Bitmap.rotate(degrees: Float): Bitmap {
    val matrix = Matrix().apply { postRotate(degrees) }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun PreviewCameraScreen() {
    cameraScreen()
}

private suspend fun Context.getCameraProvider(): ProcessCameraProvider =
    suspendCoroutine { continuation ->
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({ continuation.resume(cameraProviderFuture.get()) }, ContextCompat.getMainExecutor(this))
    }
