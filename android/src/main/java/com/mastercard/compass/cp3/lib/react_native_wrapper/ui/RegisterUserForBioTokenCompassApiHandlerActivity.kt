package com.mastercard.compass.cp3.lib.react_native_wrapper.ui

import com.mastercard.compass.base.OperationMode
import com.mastercard.compass.model.biometrictoken.Modality
import com.mastercard.compass.cp3.lib.react_native_wrapper.util.Key

class RegisterUserForBioTokenCompassApiHandlerActivity: CompassApiHandlerActivity<String>() {
    override suspend fun callCompassApi() {
        val reliantAppGuid: String = intent.getStringExtra(Key.RELIANT_APP_GUID)!!
        val programGuid: String = intent.getStringExtra(Key.PROGRAM_GUID)!!
        val consentId: String = intent.getStringExtra(Key.CONSENT_ID)!!

        val jwt = helper.generateBioTokenJWT(
            reliantAppGuid, programGuid, consentId, listOf(
                Modality.FACE,
                Modality.LEFT_PALM,
                Modality.RIGHT_PALM
            ))

        val intent = compassKernelServiceInstance.getRegisterUserForBioTokenActivityIntent(
            jwt,
            reliantAppGuid,
            OperationMode.BEST_AVAILABLE
        )

        compassApiActivityResult.launch(intent)
    }

}