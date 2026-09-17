package com.talebook.app.data.model

import com.google.gson.annotations.SerializedName

data class MyBooksAccessRequest(
    @SerializedName("invite_code") val inviteCode: String
)

data class WantsToggleRequest(
    @SerializedName("wants") val wants: Boolean
)