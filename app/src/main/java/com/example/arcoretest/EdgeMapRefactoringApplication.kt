package com.example.arcoretest

import android.app.Application
import com.naver.maps.map.NaverMapSdk

class EdgeMapRefactoringApplication: Application() {
    override fun onCreate() {
        super.onCreate()

        NaverMapSdk.getInstance(this).client =
            NaverMapSdk.NaverCloudPlatformClient(BuildConfig.CLIENT_ID)
    }
}