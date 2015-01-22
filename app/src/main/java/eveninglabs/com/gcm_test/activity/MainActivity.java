package eveninglabs.com.gcm_test.activity;

import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;

import eveninglabs.com.gcm_test.R;
import eveninglabs.com.gcm_test.activity.fragment.MainFragment;


public class MainActivity extends ActionBarActivity {

    static final String TAG = "GCM-Test-MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.i(TAG, "Enter onCreate");

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.container, new MainFragment())
                    .commit();
        }
    }

}
