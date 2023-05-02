package com.mastercard.compass.cp3.lib.react_native_wrapper.route

import android.app.Activity
import android.content.Intent
import android.util.Base64
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReadableMap
import com.mastercard.compass.cp3.lib.react_native_wrapper.CompassKernelUIController
import com.mastercard.compass.cp3.lib.react_native_wrapper.R
import com.mastercard.compass.cp3.lib.react_native_wrapper.ui.SVAOperationCompassApiHandlerActivity
import com.mastercard.compass.cp3.lib.react_native_wrapper.util.ErrorCode
import com.mastercard.compass.cp3.lib.react_native_wrapper.util.Key
import com.mastercard.compass.model.sva.SVAOperationResult
import com.mastercard.compass.wrapper.toHex
import timber.log.Timber
import java.nio.ByteBuffer
import java.security.PublicKey
import java.security.Signature


class SVAOperationAPIRoute(private val context: ReactApplicationContext, private val currentActivity: Activity?, private val helperObject: CompassKernelUIController.CompassHelper,) {
  companion object {
    val REQUEST_CODE_RANGE = 1400 until 1500
    const val GET_SVA_OPERATION_REQUEST_CODE = 1400
    private const val TAG = "SVAOperationAPIRoute"
  }
  private val kernelPublicKey: PublicKey? = helperObject.getKernelJWTPublicKey()

  fun startSVAOperationIntent(svaOperationParams: ReadableMap){

    val programGUID: String = svaOperationParams.getString("programGUID")!!
    val reliantGUID: String = svaOperationParams.getString("reliantGUID")!!
    val rId: String = svaOperationParams.getString("rID")!!
    val svaUnit: String = svaOperationParams.getMap("svaOperation")!!.getString("svaUnit")!!
    val svaAmount: Int = svaOperationParams.getMap("svaOperation")!!.getInt("svaAmount")
    val svaOperationType: String = svaOperationParams.getMap("svaOperation")!!.getString("svaOperationType")!!

    val intent = Intent(context, SVAOperationCompassApiHandlerActivity::class.java).apply {
      putExtra(Key.SVA_UNIT, svaUnit)
      putExtra(Key.RID, rId)
      putExtra(Key.RELIANT_APP_GUID, reliantGUID)
      putExtra(Key.PROGRAM_GUID, programGUID)
      putExtra(Key.SVA_AMOUNT, svaAmount)
      putExtra(Key.SVA_OPERATION_TYPE, svaOperationType)
    }

    currentActivity?.startActivityForResult(intent, GET_SVA_OPERATION_REQUEST_CODE)
  }

  fun handleSvaOperationIntentResponse(
    resultCode: Int,
    data: Intent?,
    promise: Promise
  ) {
    when (resultCode) {
      Activity.RESULT_OK -> {
        val resultMap = Arguments.createMap()
        val svaOperationResult: SVAOperationResult = data?.extras?.get(Key.DATA) as SVAOperationResult
        val verification = verifyCryptogram(
          svaOperationResult.signingInput,
          svaOperationResult.signedData
        )
        if(verification){
          resultMap.putBoolean("verification", true)
          resultMap.putMap("response", processSvaOperationResult(svaOperationResult))
        } else {
          resultMap.putBoolean("verification", false)
        }
        promise.resolve(resultMap);
      }
      Activity.RESULT_CANCELED -> {
        val code = data?.getIntExtra(Key.ERROR_CODE, ErrorCode.UNKNOWN).toString()
        val message =
          data?.getStringExtra(Key.ERROR_MESSAGE) ?: context.getString(R.string.error_unknown)
        Timber.tag(TAG).e("Error  $code  Message $message")
        promise.reject(code, Throwable(message))
      }
    }
  }

  private fun processSvaOperationResult(response: SVAOperationResult) : ReadableMap {

    val processResultMap = Arguments.createMap()
    val signingInputBuffer = ByteBuffer.wrap(response.signingInput)

    val amountByteArray = ByteArray(Int.SIZE_BYTES + 1)
    signingInputBuffer.get(amountByteArray, 0, amountByteArray.size)
    processResultMap.putString("transactionAmount", amountByteArray.toHex())

    val dateByteArray = ByteArray(3)
    signingInputBuffer.get(dateByteArray, 0, dateByteArray.size)
    processResultMap.putString("transactionAmount", dateByteArray.toHex())

    val timeByteArray = ByteArray(3)
    signingInputBuffer.get(timeByteArray, 0, timeByteArray.size)
    processResultMap.putString("transactionTime", timeByteArray.toHex())

    val transactionCountByteArray = ByteArray(Int.SIZE_BYTES)
    signingInputBuffer.get(transactionCountByteArray, 0, transactionCountByteArray.size)
    processResultMap.putString("transactionCount", transactionCountByteArray.toHex())

    val cryptogramByteArray = ByteArray(Long.SIZE_BYTES)
    signingInputBuffer.get(cryptogramByteArray, 0, cryptogramByteArray.size)
    //Log.d(TAG, "Cryptogram Value: ${cryptogramByteArray.contentToString()}")
    processResultMap.putString("cryptogramValue", cryptogramByteArray.contentToString())

    return processResultMap
  }

  fun verifyCryptogram(cryptogram: ByteArray , signedCryptogram: String?):Boolean{
    val isValid: Boolean
    val algorithm ="SHA256withRSA"
    val signature: ByteArray = Base64.decode(signedCryptogram, Base64.URL_SAFE)
    //We check if the signature is valid. We use RSA algorithm along SHA-256 digest algorithm
    isValid = Signature.getInstance(algorithm).run {
      initVerify(kernelPublicKey)
      update(cryptogram)
      verify(signature)
    }
    return isValid;
  }


}
