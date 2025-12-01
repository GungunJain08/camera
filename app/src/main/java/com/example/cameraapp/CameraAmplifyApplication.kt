package com.example.cameraapp

import android.app.Application
import android.util.Log
import com.amplifyframework.AmplifyException
import com.amplifyframework.api.aws.AWSApiPlugin
import com.amplifyframework.auth.cognito.AWSCognitoAuthPlugin
import com.amplifyframework.core.Amplify
import com.amplifyframework.storage.s3.AWSS3StoragePlugin

class CameraAmplifyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            // Add the plugins you want to use
            Amplify.addPlugin(AWSCognitoAuthPlugin()) // For user authentication
            Amplify.addPlugin(AWSS3StoragePlugin())    // For S3 file storage
            Amplify.addPlugin(AWSApiPlugin())          // For calling APIs (to talk to DynamoDB)

            // Configure Amplify
            Amplify.configure(applicationContext)
            Log.i("MyAmplifyApp", "Initialized Amplify")
        } catch (error: AmplifyException) {
            Log.e("MyAmplifyApp", "Could not initialize Amplify", error)
        }
    }
}