package com.dirror.music.util

import android.util.Log
import com.dirror.music.MyApp
import com.dirror.music.util.cache.CommonCacheInterceptor
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import java.io.File
import java.util.concurrent.TimeUnit


object HttpUtils {
    
    private const val TAG = "HttpUtils"

    const val USE_CACHE = "USE_CACHE"
    const val FORCE_CACHE = "FORCE_CACHE"

    private val cache = Cache(File("${MyApp.context.externalCacheDir}/OKHttpCache"), 50*1024*1024L)
    private val cacheControl = CacheControl.Builder().build()

    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .cache(cache)
        .addInterceptor(CommonCacheInterceptor())
        .build()


    suspend fun <T> get(url: String, clazz: Class<T>): T? = withContext(Dispatchers.IO) {
        return@withContext get(url, clazz, false)
    }

    suspend fun <T> get(url: String, clazz: Class<T>, forceCache: Boolean): T? = withContext(Dispatchers.IO) {
        val time = System.currentTimeMillis()
        var result: T? = null
        var str: String? = null
        var realUrl = url
        var iscache = false
        try {
//            Log.d(TAG, "start get $url , thread:${Thread.currentThread()}")
            val urlBuilder = HttpUrl.parse(url)?.newBuilder()?.addQueryParameter("realIP", MyApp.realIP)
            val requestBuilder = Request.Builder().get().cacheControl(cacheControl)
            if (urlBuilder == null) {
                requestBuilder.url(url)
            } else{
                requestBuilder.url(urlBuilder.build())
            }
            val request = requestBuilder.header(USE_CACHE, if (forceCache) FORCE_CACHE else "").build()
            realUrl = request.url().toString()
            val response = client.newCall(request).execute()
            str = response.body()?.string()
            result = if (clazz == String::class.java) {
                str as? T
            } else{
                gson.fromJson(str, clazz)
            }
            iscache = response.networkResponse() == null
        } catch (e:JsonSyntaxException) {
            Log.w(TAG, "json parse failed, ${e.message}\n response: $str")
        } catch (e: Exception) {
            Log.w(TAG, "get failed:${e} ,url:$realUrl")
            e.printStackTrace()
            toast(e.getString())
        }
        Log.d(TAG, "post $realUrl finished, cost: ${System.currentTimeMillis() - time} ms , isCache:${iscache}")
        return@withContext result
    }

    suspend fun <T> post(
        url: String,
        params: Map<String, String>,
        clazz: Class<T>,
        forceCache: Boolean
    ): T? = withContext(Dispatchers.IO) {
        val time = System.currentTimeMillis()
        var result: T? = null
        val bodyBuilder = FormBody.Builder()
        for ((k,v) in params) {
            bodyBuilder.add(k, v)
        }
        bodyBuilder.add("realIP", MyApp.realIP)
        var str:String? = null
        var isCache = false
        try {
//            Log.d(TAG, "start post $url , thread:${Thread.currentThread()}")
            val request = Request.Builder()
                .url(url)
                .header(USE_CACHE, if (forceCache) FORCE_CACHE else "")
                .post(bodyBuilder.build()).cacheControl(cacheControl)
                .build()

            val response = client.newCall(request).execute()
            str = response.body()?.string()
            result = gson.fromJson(str, clazz)
            isCache = response.networkResponse() == null
        } catch (e:JsonSyntaxException) {
            Log.w(TAG, "json parse failed, response: $str")
        } catch (e: Exception) {
            Log.w(TAG, "post failed:${e} ,url:$url")
            e.printStackTrace()
            toast(e.getString())
        }
        Log.d(TAG, "post $url cost: ${System.currentTimeMillis() - time} ms, isCache:$isCache")
        return@withContext result
    }

    class CommonParamsInterceptor: Interceptor {

        companion object {
            private const val METHOD_GET = "GET"
            private const val METHOD_POST = "POST"
        }


        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val requestBuilder = request.newBuilder()
            val urlBuilder = request.url().newBuilder()

            if (METHOD_GET == request.method()) { // GET方法
                // 这里可以添加一些公共get参数
                urlBuilder.addEncodedQueryParameter("realIP", MyApp.realIP)
                val httpUrl = urlBuilder.build()
                // 将最终的url填充到request中
                requestBuilder.url(httpUrl)
            } else if (METHOD_POST == request.method()) {
                val bodyBuilder = FormBody.Builder()
                // 把已有的post参数添加到新的构造器
                if (request.body() is FormBody) {
                    val formBody = request.body() as FormBody
                    for (i in 0 until formBody.size()) {
                        bodyBuilder.addEncoded(formBody.encodedName(i), formBody.encodedValue(i))
                    }
                }
                bodyBuilder.add("realIP", MyApp.realIP)
                requestBuilder.post(bodyBuilder.build())
            }
            return chain.proceed(requestBuilder.build());
        }

    }

}