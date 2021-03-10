package com.reactnativebackgroundupload.worker

import android.content.Context
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.google.common.util.concurrent.ListenableFuture
import com.reactnativebackgroundupload.NotificationHelpers
import com.reactnativebackgroundupload.model.ModelRequestMetadata
import com.reactnativebackgroundupload.model.ModelTranscodeInput
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class SplitWorker(
  context: Context,
  params: WorkerParameters
) : ListenableWorker(context, params) {
  override fun startWork(): ListenableFuture<Result> {
    return CallbackToFutureAdapter.getFuture { completer: CallbackToFutureAdapter.Completer<Result> ->
      val notificationId = inputData.getInt(ModelTranscodeInput.KEY_NOTIFICATION_ID, 1)
      val chunkSize = inputData.getInt(ModelTranscodeInput.KEY_CHUNK_SIZE, ModelTranscodeInput.DEFAULT_CHUNK_SIZE)
      val filePath = inputData.getString(ModelTranscodeInput.KEY_FILE_PATH)!!
      val uploadUrl = inputData.getString(ModelTranscodeInput.KEY_UPLOAD_URL)!!
      val metadataUrl = inputData.getString(ModelTranscodeInput.KEY_METADATA_URL)!!

      val mNotificationHelpers = NotificationHelpers(applicationContext)
      mNotificationHelpers.startNotify(notificationId, mNotificationHelpers.getSplitNotificationBuilder().build())

      val file = File(filePath)
      val result: MutableList<String> = ArrayList()

      // check whether to split video into chunks or not
      if (file.length().toInt() > chunkSize) {
        // split file into chunks and add paths to result array
        var partCounter = 1
        val buffer = ByteArray(chunkSize) // create a buffer of bytes sized as the one chunk size
        val fis = FileInputStream(file)
        val bis = BufferedInputStream(fis)
        val name = file.name
        var tmp = 0
        while (bis.read(buffer).also { tmp = it } > 0) {
          val newFile = File(applicationContext.getExternalFilesDir(null), name + "." + String.format("%03d", partCounter++)) // naming files as <inputFileName>.001, <inputFileName>.002, ...
          val out = FileOutputStream(newFile)
          out.write(buffer, 0, tmp) //tmp is chunk size. Need it for the last chunk, which could be less then 1 mb.
          out.close()
          result.add(newFile.path)
        }
        bis.close()
        fis.close()
      } else {
        // use compressed video file path instead
        result.add(filePath)
      }

      completer.set(Result.success(
        ModelRequestMetadata().createInputDataForRequestMetadata(notificationId, result.toTypedArray(), uploadUrl, metadataUrl)
      ))
    }
  }
}