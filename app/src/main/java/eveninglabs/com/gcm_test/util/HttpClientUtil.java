package eveninglabs.com.gcm_test.util;

import org.apache.http.client.HttpClient;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;

import java.io.IOException;

/**
 * HTTP Client 유틸
 */
public class HttpClientUtil {

    /**
     * Method Desc : Http Client 생성 및 설정
     *
     * @return
     */
    public static HttpClient getHttpClient() {
        HttpParams httpParams = getHttpParams();
        ClientConnectionManager clientConnectionManager = getClientConnectionManager(httpParams);

        DefaultHttpClient mDefaultHttpClient = new DefaultHttpClient(clientConnectionManager, httpParams);
        mDefaultHttpClient.setHttpRequestRetryHandler(new HttpRequestRetryHandler() {
            @Override
            public boolean retryRequest(IOException e, int exceptionCount, HttpContext httpContext) {
                // 통신 실패시 재시도 설정
                if (5 <= exceptionCount) {
                    return false;
                } else {
                    return true;
                }
            }
        });

        return mDefaultHttpClient;
    }

    /**
     * Method Desc : Http Params 생성 및 설정
     *
     * @return
     */
    private static HttpParams getHttpParams() {
        HttpParams httpParams = new BasicHttpParams();
        HttpConnectionParams.setConnectionTimeout(httpParams, 1000 * 10);
        HttpConnectionParams.setSoTimeout(httpParams, 1000 * 10);

        return httpParams;
    }

    /**
     * Method Desc : Client Connection Manager 생성 및 설정
     *
     * @param httpParams
     * @return
     */
    private static ClientConnectionManager getClientConnectionManager(HttpParams httpParams) {
        return new ThreadSafeClientConnManager(httpParams, getSchemeRegistry());
    }

    /**
     * Method Desc : Scheme Registry 생성 및 설정
     *
     * @return
     */
    private static SchemeRegistry getSchemeRegistry() {
        SchemeRegistry schemeRegistry = new SchemeRegistry();
        schemeRegistry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
        schemeRegistry.register(new Scheme("https", SSLSocketFactory.getSocketFactory(), 443));

        return schemeRegistry;
    }

}
