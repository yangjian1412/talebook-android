package com.talebook.app.data.api

import com.talebook.app.data.model.*
import retrofit2.Response
import retrofit2.http.*

interface TalebookApi {
    @GET("api/index")
    suspend fun getIndex(
        @Query("random") random: Int = 12,
        @Query("recent") recent: Int = 12
    ): Response<ApiResponse<Any>>

    @GET("api/library")
    suspend fun getLibrary(
        @Query("publisher") publisher: String? = null,
        @Query("author") author: String? = null,
        @Query("tag") tag: String? = null,
        @Query("format") format: String? = null,
        @Query("sort") sort: String? = null
    ): Response<ApiResponse<Any>>

    @GET("api/search")
    suspend fun search(
        @Query("name") query: String
    ): Response<ApiResponse<Any>>

    @GET("api/recent")
    suspend fun getRecent(): Response<ApiResponse<Any>>

    @GET("api/hot")
    suspend fun getHot(): Response<ApiResponse<Any>>

    @GET("api/book/{id}")
    suspend fun getBookDetail(
        @Path("id") bookId: Int
    ): Response<ApiResponse<Any>>

    @GET("api/book/nav")
    suspend fun getBookNav(): Response<ApiResponse<Any>>

    @GET("api/{meta}")
    suspend fun getCategoryList(
        @Path("meta") meta: String
    ): Response<ApiResponse<Any>>

    @GET("api/{meta}/{name}")
    suspend fun getCategoryBooks(
        @Path("meta") meta: String,
        @Path("name") name: String
    ): Response<ApiResponse<Any>>

    @GET("api/book/{id}/readstate")
    suspend fun getReadState(
        @Path("id") bookId: Int
    ): Response<ApiResponse<Any>>

    // 注意：talebook 后端用 self.get_argument() 取参数，必须用 form-urlencoded，不能用 JSON
    @FormUrlEncoded
    @POST("api/welcome")
    suspend fun loginWithCode(
        @Field("invite_code") code: String,
        @Field("captcha_code") captchaCode: String = "",
        @Field("lot_number") lotNumber: String = "",
        @Field("captcha_output") captchaOutput: String = "",
        @Field("pass_token") passToken: String = "",
        @Field("gen_time") genTime: String = ""
    ): Response<ApiResponse<Any>>

    @FormUrlEncoded
    @POST("api/user/sign_in")
    suspend fun loginWithPassword(
        @Field("username") username: String,
        @Field("password") password: String,
        @Field("captcha_code") captchaCode: String = "",
        @Field("lot_number") lotNumber: String = "",
        @Field("captcha_output") captchaOutput: String = "",
        @Field("pass_token") passToken: String = "",
        @Field("gen_time") genTime: String = ""
    ): Response<ApiResponse<Any>>

    @GET("api/user/sign_out")
    suspend fun signOut(): Response<ApiResponse<Any>>

    @GET("api/user/info")
    suspend fun getUserInfo(): Response<ApiResponse<Any>>

    @GET("api/reading")
    suspend fun getReadingBooks(): Response<ApiResponse<Any>>

    @GET("api/shelf")
    suspend fun getShelf(): Response<ApiResponse<Any>>

    // 这个端点服务端用 json_decode(self.request.body) 解析，必须发 JSON body
    @POST("api/book/{id}/shelf")
    suspend fun toggleShelf(
        @Path("id") bookId: Int,
        @Body body: com.talebook.app.data.model.ShelfToggleRequest
    ): Response<ApiResponse<Any>>

    // 人机验证相关
    @GET("api/captcha/config")
    suspend fun getCaptchaConfig(): Response<CaptchaConfigResponse>

    @GET("api/captcha/image")
    suspend fun getCaptchaImage(): Response<CaptchaImageResponse>
}
