package eu.siacs.conversations;


import com.androidnetworking.AndroidNetworking;
import com.github.druk.rxdnssd.RxDnssd;
import com.github.druk.rxdnssd.RxDnssdBindable;
import com.github.druk.rxdnssd.RxDnssdEmbedded;
import com.owncloud.android.MainApp;
import com.squareup.leakcanary.LeakCanary;


import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.support.annotation.NonNull;
import android.util.Log;


import spreedbox.me.app.R;

public class SpreedboxApplication extends MainApp {
    private static final String TAG = "SpreedboxApplication";
    private RxDnssd mRxDnssd;
 
    public void onCreate() {
        super.onCreate();
        if (LeakCanary.isInAnalyzerProcess(this)) {
            return;
        }
        LeakCanary.install(this);
        mRxDnssd = createDnssd();
        AndroidNetworking.initialize(getApplicationContext());
    }

    public static RxDnssd getRxDnssd(@NonNull Context context){
        return ((SpreedboxApplication)context.getApplicationContext()).mRxDnssd;
    }

    private RxDnssd createDnssd(){
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN){
            Log.i(TAG, "Using embedded version of dns sd because of API < 16");
            return new RxDnssdEmbedded();
        }
        if (Build.VERSION.RELEASE.contains("4.4.2") && Build.MANUFACTURER.toLowerCase().contains("samsung")){
            Log.i(TAG, "Using embedded version of dns sd because of Samsung 4.4.2");
            return new RxDnssdEmbedded();
        }
        Log.i(TAG, "Using systems dns sd daemon");
        return new RxDnssdBindable(this);
    }
}