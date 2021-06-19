package cityfreqs.com.iviewproxy;

import android.app.Activity;
import android.app.UiModeManager;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.Log;

/*
 * TV Activity class
 */
public class TvActivity extends Activity {

    private static final String TAG = "AndIviewProxyTV";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tv);




        UiModeManager uiModeManager = (UiModeManager) getSystemService(UI_MODE_SERVICE);
        if (uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION) {
            Log.d(TAG, "Running on a TV Device");
            // as it should be here
        }
        else {
            Log.d(TAG, "Running on a non-TV Device");
            // erm, now what? assume mobile and start mainActivity?
        }
    }
}