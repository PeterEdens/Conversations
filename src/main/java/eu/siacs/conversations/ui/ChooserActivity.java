package eu.siacs.conversations.ui;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.os.Handler;
import android.view.MotionEvent;
import android.view.View;

import com.owncloud.android.authentication.AccountUtils;
import com.owncloud.android.ui.activity.FileDisplayActivity;

import org.appspot.apprtc.ConnectActivity;

import spreedbox.me.app.R;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
public class ChooserActivity extends AppCompatActivity {


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Account account = AccountUtils.getCurrentOwnCloudAccount(this);
        if (account == null) {
            startActivity(new Intent(this, SpreedboxAuthenticatorActivity.class));
        }

        setContentView(R.layout.activity_chooser);

        // Hide UI first
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }

        findViewById(R.id.secure_chat_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent startConversationActivity = new Intent(view.getContext(), StartConversationActivity.class);
                startConversationActivity.putExtra("init", true);
                startActivity(startConversationActivity);
            }
        });

        findViewById(R.id.video_call_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent connectActivity = new Intent(view.getContext(), ConnectActivity.class);
                Account account = AccountUtils.getCurrentOwnCloudAccount(view.getContext());
                if (account != null) {
                    AccountManager accountMgr = AccountManager.get(view.getContext());
                    String serverUrl = accountMgr.getUserData(account, com.owncloud.android.lib.common.accounts.AccountUtils.Constants.KEY_OC_BASE_URL);
                    String displayName = accountMgr.getUserData(account, com.owncloud.android.lib.common.accounts.AccountUtils.Constants.KEY_DISPLAY_NAME);
                    connectActivity.putExtra(ConnectActivity.EXTRA_SERVERURL, serverUrl);
                    connectActivity.putExtra(ConnectActivity.EXTRA_DISPLAYNAME, displayName);
                }
                startActivity(connectActivity);
            }
        });

        findViewById(R.id.files_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent fileDisplayIntent = new Intent(getApplicationContext(),
                        FileDisplayActivity.class);
                //fileDisplayIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(fileDisplayIntent);
            }
        });
    }
}
