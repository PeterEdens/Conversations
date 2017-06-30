package eu.siacs.conversations;


import com.owncloud.android.MainApp;
import com.squareup.leakcanary.LeakCanary;


import android.app.Application;
import android.content.Context;
import android.content.Intent;


import spreedbox.me.app.R;

public class SpreedboxApplication extends MainApp {

 
    public void onCreate() {
        super.onCreate();
        if (LeakCanary.isInAnalyzerProcess(this)) {
            return;
        }
        LeakCanary.install(this);
    }

}