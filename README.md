# Camera App with GeoTagging, AWS S3, and DynamoDB

This document outlines the steps to implement a camera application with the following features:
- Capture Photo with GeoTagging
- Save photo and metadata to a local database
- Upload the photo to AWS S3
- Save photo metadata to DynamoDB
- Display all photos on a Google Map

## 1. Add Dependencies

You will need to add the following dependencies to your `app/build.gradle.kts` file:

```kotlin
// For camera functionality
implementation("androidx.camera:camera-core:1.3.1")
implementation("androidx.camera:camera-camera2:1.3.1")
implementation("androidx.camera:camera-lifecycle:1.3.1")
implementation("androidx.camera:camera-view:1.3.1")

// For location services
implementation("com.google.android.gms:play-services-location:21.0.1")

// For local database (Room)
implementation("androidx.room:room-runtime:2.6.1")
ksp("androidx.room:room-compiler:2.6.1")
implementation("androidx.room:room-ktx:2.6.1")

// For AWS S3 and DynamoDB
implementation("com.amplifyframework:aws-storage-s3:2.14.9")
implementation("com.amplifyframework:aws-api-rest:2.14.9") // For DynamoDB via API Gateway
implementation("com.amplifyframework:aws-auth-cognito:2.14.9")

// For Google Maps
implementation("com.google.android.gms:play-services-maps:18.2.0")
```

## 2. Request Permissions

Ensure you have the necessary permissions in your `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.INTERNET" />
```

## 3. Capture Photo with Geotag

Use CameraX to capture an image. While capturing, use the `FusedLocationProviderClient` to get the current location (latitude and longitude).

## 4. Save to Local DB (Room)

Create a Room database to store the photo's URI and its location.

-   **Entity:** Create a `Photo` entity with columns for `id`, `imageUri`, `latitude`, and `longitude`.
-   **DAO:** Create a Data Access Object (DAO) with methods to insert and retrieve photos.
-   **Database:** Create a `RoomDatabase` class.

## 5. Upload to AWS S3

Use the AWS Amplify Storage category to upload the image file to your S3 bucket. You will need to configure Amplify for your project first.

## 6. Save Metadata to DynamoDB

After a successful upload to S3, save the photo's metadata (like the S3 key, location, and timestamp) to a DynamoDB table. You can do this by invoking an API Gateway endpoint that triggers a Lambda function to write to DynamoDB.

## 7. Display Photos on Google Maps

-   Add a `MapView` to your layout.
-   Retrieve the photo data (with locations) from your local Room database or from DynamoDB.
-   For each photo, add a marker to the Google Map at its latitude and longitude.

This provides a high-level overview of the implementation. Each step involves more detailed code which you can write based on this guide.
