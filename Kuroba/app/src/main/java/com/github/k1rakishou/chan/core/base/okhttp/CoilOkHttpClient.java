package com.github.k1rakishou.chan.core.base.okhttp;

import android.content.Context;

import com.github.k1rakishou.chan.Chan;
import com.github.k1rakishou.chan.core.di.HttpLoggingInterceptorInstaller;
import com.github.k1rakishou.chan.core.manager.ProxyStorage;
import com.github.k1rakishou.chan.core.net.KurobaProxySelector;

import java.io.File;

import kotlin.Lazy;
import okhttp3.Cache;
import okhttp3.Dns;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;


public class CoilOkHttpClient {
    private static final String IMAGE_CACHE_DIR = "coil_image_cache_dir";
    private static final long ONE_MB = 1024 * 1024;
    private static final long IMAGE_CACHE_MAX_SIZE = 100 * ONE_MB;

    private final Context applicationContext;
    private final Dns okHttpDns;
    private final Chan.OkHttpProtocols okHttpProtocols;
    private final Lazy<HttpLoggingInterceptor> loggingInterceptorLazyKt;
    private final ProxyStorage proxyStorage;

    private OkHttpClient coilClient;

    public CoilOkHttpClient(
            Context applicationContext,
            Dns okHttpDns,
            Chan.OkHttpProtocols okHttpProtocols,
            ProxyStorage proxyStorage,
            Lazy<HttpLoggingInterceptor> loggingInterceptorLazyKt
    ) {
        this.applicationContext = applicationContext;
        this.okHttpDns = okHttpDns;
        this.okHttpProtocols = okHttpProtocols;
        this.proxyStorage = proxyStorage;
        this.loggingInterceptorLazyKt = loggingInterceptorLazyKt;
    }

    public OkHttpClient getCoilClient() {
        if (coilClient == null) {
            synchronized (this) {
                if (coilClient == null) {
                    File imageCacheDir = new File(applicationContext.getCacheDir(), IMAGE_CACHE_DIR);
                    if (!imageCacheDir.exists() && !imageCacheDir.mkdirs()) {
                        throw new IllegalStateException("mkdirs failed to create " + imageCacheDir.getAbsolutePath());
                    }

                    Cache cache = new Cache(imageCacheDir, IMAGE_CACHE_MAX_SIZE);

                    KurobaProxySelector kurobaProxySelector = new KurobaProxySelector(
                            proxyStorage,
                            ProxyStorage.ProxyActionType.SiteMediaPreviews
                    );

                    OkHttpClient.Builder builder = new OkHttpClient.Builder()
                            .protocols(okHttpProtocols.getProtocols())
                            .proxySelector(kurobaProxySelector)
                            .cache(cache)
                            .dns(okHttpDns);

                    HttpLoggingInterceptorInstaller.install(builder, loggingInterceptorLazyKt);
                    coilClient = builder.build();
                }
            }
        }

        return coilClient;
    }

}