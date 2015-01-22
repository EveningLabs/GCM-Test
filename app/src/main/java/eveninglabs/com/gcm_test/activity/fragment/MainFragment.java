package eveninglabs.com.gcm_test.activity.fragment;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.gcm.GoogleCloudMessaging;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.StringEntity;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.concurrent.atomic.AtomicInteger;

import eveninglabs.com.gcm_test.R;
import eveninglabs.com.gcm_test.util.HttpClientUtil;

/**
 * A simple {@link Fragment} subclass.
 */
public class MainFragment extends Fragment {

    public static final String EXTRA_MESSAGE = "message";
    public static final String PROPERTY_REG_ID = "registration_id";
    private static final String PROPERTY_APP_VERSION = "appVersion";
    private final static int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;

    /**
     * Tag used on log messages.
     */
    static final String TAG = "GCM-Test-MainFragment";

    TextView display;
    EditText message;
    Button submit;

    GoogleCloudMessaging gcm;
    AtomicInteger msgId = new AtomicInteger();
    SharedPreferences prefs;
    Context context;

    String regid;

    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String PARAM_RECEIVE_MESSAGE = "receive_message";

    private String receiveMessage;

    public MainFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param receiveMessage Parameter 1.
     * @return A new instance of fragment MainFragment.
     */
    public static MainFragment newInstance(String receiveMessage) {
        Bundle mBundle = new Bundle();
        mBundle.putString(PARAM_RECEIVE_MESSAGE, receiveMessage);

        MainFragment mMainFragment = new MainFragment();
        mMainFragment.setArguments(mBundle);

        return mMainFragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (null != getArguments()) {
            receiveMessage = getArguments().getString(PARAM_RECEIVE_MESSAGE);
        }

        context = getActivity().getApplicationContext();

        // Check device for Play Services APK. If check succeeds, proceed with GCM registration.
        // 플레이 서비스 가능 여부 체크
        if (checkPlayServices()) {
            gcm = GoogleCloudMessaging.getInstance(getActivity());
            regid = getRegistrationId(context);

            // 레지스트레이션 아이디가 발급되어 있지 않을 경우 발급 요청
            if (regid.isEmpty()) {
                registerInBackground();
            }
        } else {
            Log.i(TAG, "No valid Google Play Services APK found.");
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_main, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        display = (TextView) view.findViewById(R.id.display);
        message = (EditText) view.findViewById(R.id.message);

        // 푸쉬 알람을 받고 노티피케이션 알림을 통해서 들어온 경우
        if (null != receiveMessage && !receiveMessage.isEmpty()) {
            display.append("\n" + new SimpleDateFormat("HHmmss").format(Calendar.getInstance().getTime()) + " RECEIVE: " + receiveMessage);
        }

        submit = (Button) view.findViewById(R.id.submit);
        submit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                display.append("\n" + new SimpleDateFormat("HHmmss").format(Calendar.getInstance().getTime()) + " PUSH: " + message.getText().toString());

                final JSONObject params = new JSONObject();
                try {
                    JSONArray registrationIds = new JSONArray();
                    registrationIds.put(getGCMPreferences(context).getString(PROPERTY_REG_ID, ""));
                    params.put("registration_ids", registrationIds);

                    params.put("collapse_key", getString(R.string.app_name));
                    params.put("time_to_live", 1);
                    params.put("delay_while_idle", true);

                    JSONObject data = new JSONObject();
                    data.put("title", getString(R.string.app_name));
                    data.put("body", message.getText().toString());
                    params.put("data", data);

                    Log.i(TAG, params.toString());

                    sendMessage(params);
                } catch (Exception e) {
                    Log.e(TAG, e.toString());
                }
            }
        });
    }

    /**
     * Check the device to make sure it has the Google Play Services APK. If
     * it doesn't, display a dialog that allows users to download the APK from
     * the Google Play Store or enable it in the device's system settings.
     * 플레이 서비스 가능 여부 확인
     */
    private boolean checkPlayServices() {
        Log.i(TAG, "Enter checkPlayServices");
        int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(getActivity());
        if (resultCode != ConnectionResult.SUCCESS) {
            if (GooglePlayServicesUtil.isUserRecoverableError(resultCode)) {
                GooglePlayServicesUtil.getErrorDialog(resultCode, getActivity(), PLAY_SERVICES_RESOLUTION_REQUEST).show();
            } else {
                Log.i(TAG, "This device is not supported.");
                Toast.makeText(getActivity(), "This device is not supported.", Toast.LENGTH_SHORT).show();
            }
            return false;
        }
        return true;
    }

    /**
     * Gets the current registration ID for application on GCM service.
     * <p/>
     * If result is empty, the app needs to register.
     *
     * @return registration ID, or empty string if there is no existing registration ID.
     * 레지스트레이션 아이디 가져오기
     */
    private String getRegistrationId(Context context) {
        Log.i(TAG, "Enter getRegistrationId");
        final SharedPreferences prefs = getGCMPreferences(context);
        String registrationId = prefs.getString(PROPERTY_REG_ID, "");
        if (registrationId.isEmpty()) {
            Log.i(TAG, "Registration not found.");
            return "";
        }
        // Check if app was updated; if so, it must clear the registration ID
        // since the existing regID is not guaranteed to work with the new
        // app version.
        int registeredVersion = prefs.getInt(PROPERTY_APP_VERSION, Integer.MIN_VALUE);
        int currentVersion = getAppVersion(context);
        if (registeredVersion != currentVersion) {
            Log.i(TAG, "App version changed.");
            return "";
        }
        return registrationId;
    }

    /**
     * @return Application's {@code SharedPreferences}.
     */
    private SharedPreferences getGCMPreferences(Context context) {
        Log.i(TAG, "Enter getGCMPreferences");
        // This sample app persists the registration ID in shared preferences, but
        // how you store the regID in your app is up to you.
        return getActivity().getSharedPreferences(MainFragment.class.getSimpleName(), Context.MODE_PRIVATE);
    }

    /**
     * @return Application's version code from the {@code PackageManager}.
     */
    private static int getAppVersion(Context context) {
        Log.i(TAG, "Enter getAppVersion");
        try {
            PackageInfo packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return packageInfo.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            // should never happen
            throw new RuntimeException("Could not get package name: " + e);
        }
    }

    /**
     * Registers the application with GCM servers asynchronously.
     * <p/>
     * Stores the registration ID and app versionCode in the application's
     * shared preferences.
     * 레지스트레이션 아이디를 발급 요청하여 발급된 아이디를 내부적으로 저장하고 서드 파티 서버에 전송하여 사용할 수 있도록 한다.
     */
    private void registerInBackground() {
        Log.i(TAG, "Enter registerInBackground");
        new AsyncTask<Void, Void, String>() {
            @Override
            protected String doInBackground(Void... params) {
                String msg = "";
                try {
                    if (gcm == null) {
                        gcm = GoogleCloudMessaging.getInstance(context);
                    }
                    regid = gcm.register(getString(R.string.google_play_project_number));
                    msg = "Device registered, registration ID=" + regid;

                    // You should send the registration ID to your server over HTTP,
                    // so it can use GCM/HTTP or CCS to send messages to your app.
                    // The request to your server should be authenticated if your app
                    // is using accounts.
                    sendRegistrationIdToBackend();

                    // For this demo: we don't need to send it because the device
                    // will send upstream messages to a server that echo back the
                    // message using the 'from' address in the message.

                    // Persist the regID - no need to register again.
                    storeRegistrationId(context, regid);
                } catch (IOException ex) {
                    msg = "Error :" + ex.getMessage();
                    // If there is an error, don't just keep trying to register.
                    // Require the user to click a button again, or perform
                    // exponential back-off.
                }
                return msg;
            }

            @Override
            protected void onPostExecute(String msg) {
                display.append("\n" + new SimpleDateFormat("HHmmss").format(Calendar.getInstance().getTime()) + " Registration ID: " + msg);
            }
        }.execute(null, null, null);
    }

    /**
     * Sends the registration ID to your server over HTTP, so it can use GCM/HTTP
     * or CCS to send messages to your app. Not needed for this demo since the
     * device sends upstream messages to a server that echoes back the message
     * using the 'from' address in the message.
     * 서버로 레지스트레이션 아이디를 보내서 메세지 전송에 사용한다.
     */
    private void sendRegistrationIdToBackend() {
        Log.i(TAG, "Enter sendRegistrationIdToBackend");
        // Your implementation here.
        // 서버로 Registration ID를 보내서 메세지 전송에 사용한다.
    }

    /**
     * Stores the registration ID and app versionCode in the application's
     * {@code SharedPreferences}.
     * 발급된 레지스트레이션 아이디를 내부적으로 저장한다.
     *
     * @param context application's context.
     * @param regId   registration ID
     */
    private void storeRegistrationId(Context context, String regId) {
        Log.i(TAG, "Enter storeRegistrationId");
        final SharedPreferences prefs = getGCMPreferences(context);
        int appVersion = getAppVersion(context);
        Log.i(TAG, "Saving regId on app version " + appVersion);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(PROPERTY_REG_ID, regId);
        editor.putInt(PROPERTY_APP_VERSION, appVersion);
        editor.commit();
    }

    /**
     * 단말기에서 메세지(푸쉬) 보내기
     *
     * @param params
     */
    private void sendMessage(final JSONObject params) {
        Log.i(TAG, "Enter sendMessage");

        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpClient mHttpClient = null;
                HttpResponse mHttpResponse = null;
                HttpRequestBase mHttpRequestBase = new HttpPost("https://android.googleapis.com/gcm/send");
                StringEntity mStringEntity = null;

                String content = "";

                try {
                    ((HttpPost) mHttpRequestBase).setHeader("Content-Type", "application/json");
                    ((HttpPost) mHttpRequestBase).setHeader("Authorization", "key=" + getString(R.string.key_google_public_api_access_server));

                    mStringEntity = new StringEntity(params.toString(), HTTP.UTF_8);
                    ((HttpPost) mHttpRequestBase).setEntity(mStringEntity);

                    // HttpClient 생성
                    mHttpClient = HttpClientUtil.getHttpClient();
                    mHttpResponse = mHttpClient.execute(mHttpRequestBase);

                    int statusCode = mHttpResponse.getStatusLine().getStatusCode();

                    // 작업이 실패했을 경우
                    if (HttpStatus.SC_OK != statusCode) {
                        Log.e(TAG, "getStatusCode() = " + statusCode);
                    }

                    if (mHttpResponse.getEntity() == null || mHttpResponse.getEntity().getContentLength() == 0) {
                        Log.e(TAG, "getEntity() = null or 0");
                    }

                    content = EntityUtils.toString(mHttpResponse.getEntity(), HTTP.UTF_8);

                    Log.i(TAG, new JSONObject(content).toString());
                } catch (Exception e) {
                    Log.e(TAG, e.toString());
                } finally {
                    // HttpClient 리소스 해제
                    if (mHttpClient != null && mHttpClient.getConnectionManager() != null) {
                        mHttpClient.getConnectionManager().shutdown();
                    }
                }
            }
        }).start();
    }

}
