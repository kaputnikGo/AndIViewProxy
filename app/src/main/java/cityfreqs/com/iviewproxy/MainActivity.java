package cityfreqs.com.iviewproxy;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;

@SuppressLint("StaticFieldLeak")
public class MainActivity extends AppCompatActivity implements ActivityCompat.OnRequestPermissionsResultCallback{

    public static final String VERSION = "1.1";
    private static final String TAG = "AndIViewProxy";
    private static final int REQUEST_INTERNET_PERMISSION = 1;
    // Used to load the libraries on application startup.
    static {
        System.loadLibrary("native-lib");
        System.loadLibrary("node");
    }

    //one instance of node running in the background.
    public static boolean _startedNodeAlready=false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // permissions ask:
        // check API version, above 23 permissions are asked at runtime
        // if API version < 23 (6.x) fallback is manifest.xml file permission declares

        if (ContextCompat.checkSelfPermission(MainActivity.this,
                Manifest.permission.INTERNET) != PackageManager.PERMISSION_GRANTED) {
            // continue
            Log.d(TAG, "perms granted.");
        }
        else if (ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this,
                    Manifest.permission.INTERNET)) {
                Log.d(TAG, "show perms rationale.");
                String message = getResources().getString(R.string.perms_state_2_1) + "\n\n";
                message += getResources().getString(R.string.perms_state_2_2) + "\n\n";
                message += Manifest.permission.INTERNET;

                showPermissionsDialog(message, (dialog, which) -> ActivityCompat.requestPermissions(MainActivity.this,
                        new String[]{Manifest.permission.INTERNET},
                        REQUEST_INTERNET_PERMISSION));
        }
        else {
            // no reasoning, show perms request
            Log.d(TAG, "show perms request.");
            ActivityCompat.requestPermissions(MainActivity.this,
                        new String[]{Manifest.permission.INTERNET},
                        REQUEST_INTERNET_PERMISSION);
        }

        final TextView textViewVersions = findViewById(R.id.tvVersions);
        final Button buttonVersions = findViewById(R.id.btVersions);

        buttonVersions.setOnClickListener(v -> {
            //Network operations should be done in the background.
            new AsyncTask<Void,Void,String>() {
                @Override
                protected String doInBackground(Void... params) {
                    StringBuilder nodeResponse= new StringBuilder();
                    try {
                        //URL localNodeServer = new URL("http://localhost:1984/");
                        nodeResponse.append("localNodeServer running at http://localhost:1984");
                    }
                    catch (Exception ex) {
                        nodeResponse = new StringBuilder(ex.toString());
                    }
                    return nodeResponse.toString();
                }
                @Override
                protected void onPostExecute(String result) {
                    textViewVersions.setText(result);
                    buttonVersions.setText(R.string.buttonRunning);
                }
            }.execute();
        });

        // nodeseses
        if( !_startedNodeAlready ) {
            Log.d(TAG, "not node started already.");
            _startedNodeAlready=true;
            new Thread(() -> {
                //The path where we expect the node project to be at runtime.
                String nodeDir=getApplicationContext().getFilesDir().getAbsolutePath()+"/iview-proxy";
                Log.d(TAG, "looking for string node: " + nodeDir);
                if (wasAPKUpdated()) {
                    //Recursively delete any existing nodejs-project.
                    File nodeDirReference=new File(nodeDir);
                    Log.d(TAG, "check if exists.");
                    if (nodeDirReference.exists()) {
                        deleteFolderRecursively(new File(nodeDir));
                    }
                    //Copy the node project from assets into the application's data path.
                    Log.d(TAG, "call copy assets folder.");
                    copyAssetFolder(getApplicationContext().getAssets(), "iview-proxy", nodeDir);

                    saveLastUpdateTime();
                }
                Log.d(TAG, "call start node.");
                startNodeWithArguments(new String[]{"node",
                        nodeDir+"/app.js"
                });
            }).start();
        }
        else {
            Log.d(TAG, "else started node.");
        }
    }


    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    public native Integer startNodeWithArguments(String[] arguments);

    //
    // UTILITY FUNCTIONS
    //
    /**
     * copy the nodejs-project into the Application's FilesDir because
     * Android Application's APK is an archive file and Node.js won't be able to start
     */
    private static boolean deleteFolderRecursively(File file) {
        Log.d(TAG, "delete folder called.");
        try {
            boolean res=true;
            for (File childFile : Objects.requireNonNull(file.listFiles())) {
                if (childFile.isDirectory()) {
                    res &= deleteFolderRecursively(childFile);
                } else {
                    res &= childFile.delete();
                }
            }
            res &= file.delete();
            return res;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private static boolean copyAssetFolder(AssetManager assetManager, String fromAssetPath, String toPath) {
        try {
            String[] files = assetManager.list(fromAssetPath);
            boolean res = true;

            if (files.length==0) {
                //If it's a file, it won't have any assets "inside" it.
                res = copyAsset(assetManager,
                        fromAssetPath,
                        toPath);
            } else {
                new File(toPath).mkdirs();
                for (String file : files)
                    res &= copyAssetFolder(assetManager,
                            fromAssetPath + "/" + file,
                            toPath + "/" + file);
            }
            return res;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private static boolean copyAsset(AssetManager assetManager, String fromAssetPath, String toPath) {
        Log.d(TAG, "call copy asset.");
        InputStream in = null;
        OutputStream out = null;
        try {
            in = assetManager.open(fromAssetPath);
            new File(toPath).createNewFile();
            out = new FileOutputStream(toPath);
            copyFile(in, out);
            in.close();
            in = null;
            out.flush();
            out.close();
            out = null;
            return true;
        } catch(Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private static void copyFile(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
    }

    /**
     * Copy the nodejs-project only if and after an APK change, ie once is enough
     */

    private boolean wasAPKUpdated() {
        SharedPreferences prefs = getApplicationContext().getSharedPreferences("NODEJS_MOBILE_PREFS", Context.MODE_PRIVATE);
        long previousLastUpdateTime = prefs.getLong("NODEJS_MOBILE_APK_LastUpdateTime", 0);
        long lastUpdateTime = 1;
        try {
            PackageInfo packageInfo = getApplicationContext().getPackageManager().getPackageInfo(getApplicationContext().getPackageName(), 0);
            lastUpdateTime = packageInfo.lastUpdateTime;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return (lastUpdateTime != previousLastUpdateTime);
    }

    private void saveLastUpdateTime() {
        Log.d(TAG, "call save last update.");
        long lastUpdateTime = 1;
        try {
            Log.d(TAG, "get package info.");
            PackageInfo packageInfo = getApplicationContext().getPackageManager().getPackageInfo(getApplicationContext().getPackageName(), 0);
            lastUpdateTime = packageInfo.lastUpdateTime;
        }
        catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        SharedPreferences prefs = getApplicationContext().getSharedPreferences("NODEJS_MOBILE_PREFS", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putLong("NODEJS_MOBILE_APK_LastUpdateTime", lastUpdateTime);
        editor.apply();
        Log.d(TAG, "fin editor apply.");
    }

    //
    //PERMISSIONS - internet
    //
    private void showPermissionsDialog(String message, DialogInterface.OnClickListener okListener) {
        new AlertDialog.Builder(MainActivity.this)
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton(getResources().getString(R.string.dialog_button_continue), okListener)
                .create()
                .show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_INTERNET_PERMISSION) {
            // Check for RECORD_AUDIO
            if (!(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                // Permission Denied
                Toast toast;
                toast = Toast.makeText(getApplicationContext(), getResources().getString(R.string.perms_state_3), Toast.LENGTH_LONG);
                toast.show();
                closeApp();
            }
        }
        else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    private void closeApp() {
        Log.d(TAG, getResources().getString(R.string.perms_state_4));
        finishAffinity();
    }
}