package com.reactnativebackgroundupload

import android.text.TextUtils
import android.util.Log
import com.facebook.react.bridge.ReactApplicationContext
import com.linkedin.android.litr.TransformationListener
import com.linkedin.android.litr.analytics.TrackTransformationInfo

class MediaTransformationListener(private val reactContext: ReactApplicationContext, private val requestId: String) : TransformationListener {
  override fun onStarted(id: String) {
    if (TextUtils.equals(requestId, id)) {
      Log.d("TRANSCODE", "start")
    }
  }

  override fun onProgress(id: String, progress: Float) {
    if (TextUtils.equals(requestId, id)) {
      Log.d("TRANSCODE", (progress * 100).toString())
    }
  }

  override fun onCompleted(id: String, trackTransformationInfos: MutableList<TrackTransformationInfo>?) {
    if (TextUtils.equals(requestId, id)) {
      Log.d("TRANSCODE", "complete")
    }
  }

  override fun onCancelled(id: String, trackTransformationInfos: MutableList<TrackTransformationInfo>?) {
    if (TextUtils.equals(requestId, id)) {
      Log.d("TRANSCODE", "cancel")
    }
  }

  override fun onError(id: String, cause: Throwable?, trackTransformationInfos: MutableList<TrackTransformationInfo>?) {
    if (TextUtils.equals(requestId, id)) {
      Log.e("TRANSCODE", "error", cause)
    }
  }

}
