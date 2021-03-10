package com.reactnativebackgroundupload.worker

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.work.*
import com.androidnetworking.AndroidNetworking
import com.androidnetworking.error.ANError
import com.androidnetworking.interfaces.ParsedRequestListener
import com.google.common.util.concurrent.ListenableFuture
import com.reactnativebackgroundupload.NotificationHelpers
import com.reactnativebackgroundupload.model.*

internal interface RequestMetadataCallback {
  fun success()
  fun failure()
}

class RequestMetadataWorker(
  context: Context,
  params: WorkerParameters
) : ListenableWorker(context, params) {
  private val mNotificationHelpers = NotificationHelpers(applicationContext)

  override fun startWork(): ListenableFuture<Result> {
    return CallbackToFutureAdapter.getFuture { completer: CallbackToFutureAdapter.Completer<Result> ->
      val notificationId = inputData.getInt(ModelRequestMetadata.KEY_NOTIFICATION_ID, 1)

      val callback: RequestMetadataCallback = object : RequestMetadataCallback {
        override fun success() {
          completer.set(Result.success())
        }
        override fun failure() {
          mNotificationHelpers.startNotify(
            notificationId,
            mNotificationHelpers.getFailureNotificationBuilder().build()
          )
          completer.set(Result.failure())
        }
//        override fun retry() {
//          if (runAttemptCount > 2) {
//            completer.set(Result.failure())
//          } else {
//            completer.set(Result.retry())
//          }
//        }
      }
      val uploadUrl = inputData.getString(ModelRequestMetadata.KEY_UPLOAD_URL)!!
      val metadataUrl = inputData.getString(ModelRequestMetadata.KEY_METADATA_URL)!!
      val chunkPaths = inputData.getStringArray(ModelRequestMetadata.KEY_CHUNK_PATH_ARRAY)!!
      requestMetadata(metadataUrl, uploadUrl, chunkPaths, notificationId, callback)
      callback
    }
  }

  private fun requestMetadata(metadataUrl: String, uploadUrl: String, chunkPaths: Array<String>, notificationId: Int, callback: RequestMetadataCallback) {
    val chunkSize = chunkPaths.size
    AndroidNetworking.post(metadataUrl).apply {
      addBodyParameter("cto", "$chunkSize")
      addBodyParameter("ext", "mp4")
    }.build().getAsObject(ModelMetadataResponse::class.java, object : ParsedRequestListener<ModelMetadataResponse> {
      override fun onResponse(response: ModelMetadataResponse) {
        val metadata = response.data
        if (metadata != null && response.status == "1") {
          Log.d("METADATA:", "fileName: ${metadata.filename}")
          Log.d("METADATA:", "url: ${metadata.url}")
          startUploadWorker(metadata, uploadUrl, chunkPaths, chunkSize, notificationId, callback)
        } else {
          Log.wtf("METADATA:", "no metadata")
          callback.failure()
        }
      }
      override fun onError(error: ANError) {
        // handle error
        Log.wtf("METADATA:", "$error")
        callback.failure()
      }
    })
  }

  @SuppressLint("EnqueueWork")
  private fun startUploadWorker(data: VideoMetadata, uploadUrl: String, chunkPaths: Array<String>, chunkSize: Int, notificationId: Int, callback: RequestMetadataCallback) {
    val fileName = data.filename
    val hashMap = data.hashes
    if (hashMap != null && fileName != null) {
      // init work manager
      val workManager = WorkManager.getInstance(applicationContext)
      val workConstraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED) // constraints worker with network condition
        .build()
      var workContinuation: WorkContinuation? = null

      // add upload worker to queue
      for ((key, value) in hashMap) {
        val prt = key.toInt()
        val uploadRequest = OneTimeWorkRequestBuilder<UploadWorker>().apply {
          setConstraints(workConstraints)
          setInputData(
            ModelUploadInput().createInputDataForUpload(uploadUrl, fileName, chunkPaths[prt - 1], value, prt, chunkSize, notificationId)
          )
        }.build()
        workContinuation = workContinuation?.then(uploadRequest)
          ?: workManager.beginWith(uploadRequest)
      }
      // add clean up work
      val clearRequest = OneTimeWorkRequestBuilder<ClearNotificationWorker>()
        .setInputData(
          ModelClearNotification().createInputDataForClearNotification(notificationId)
        ).build()
      workContinuation = workContinuation?.then(clearRequest)
      workContinuation?.enqueue()
      callback.success()
    } else {
      callback.failure()
    }
  }
}