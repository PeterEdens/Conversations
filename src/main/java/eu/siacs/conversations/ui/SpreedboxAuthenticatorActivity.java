package eu.siacs.conversations.ui;

import android.annotation.TargetApi;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.IBinder;
import android.preference.PreferenceManager;

import com.owncloud.android.authentication.AuthenticatorActivity;
import com.owncloud.android.lib.common.operations.RemoteOperationResult;

import java.net.MalformedURLException;
import java.net.URL;

import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xmpp.jid.InvalidJidException;
import eu.siacs.conversations.xmpp.jid.Jid;
import spreedbox.me.app.R;

public class SpreedboxAuthenticatorActivity extends AuthenticatorActivity{

    public XmppConnectionService xmppConnectionService;
    public boolean xmppConnectionServiceBound = false;

    protected ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            XmppConnectionService.XmppConnectionBinder binder = (XmppConnectionService.XmppConnectionBinder) service;
            xmppConnectionService = binder.getService();
            xmppConnectionServiceBound = true;

        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            xmppConnectionServiceBound = false;
        }
    };

    @Override
    protected void onStart() {
        super.onStart();
        if (!xmppConnectionServiceBound) {
            connectToBackend();
        } else {

        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    protected boolean shouldRegisterListeners() {
        if  (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            return !isDestroyed() && !isFinishing();
        } else {
            return !isFinishing();
        }
    }

    public void connectToBackend() {
        Intent intent = new Intent(this, XmppConnectionService.class);
        intent.setAction("ui");
        startService(intent);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (xmppConnectionServiceBound) {
            unbindService(mConnection);
            xmppConnectionServiceBound = false;
        }
    }

    public void onOkClick() {
        super.onOkClick();

        /// get basic credentials entered by user
        String username = mUsernameInput.getText().toString().trim();
        String password = mPasswordInput.getText().toString();
        String urlstring = mServerInfo.mBaseUrl;
        URL url = null;

        try {
            url = new URL(urlstring);
        }
        catch (MalformedURLException e) {
            e.printStackTrace();
        }

        if (url != null) {
            // save the last server
            String last_server = url.getHost();
            if (url.getPort() != -1) {
                last_server += ":" + url.getPort();
            }
            PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).edit().putString("last_server", last_server).commit();

            Jid jid = null;
            try {
                jid = Jid.fromString(username + "@" + url.getHost());
            } catch (final InvalidJidException e) {
                e.printStackTrace();
            }

            if (jid != null) {
                Account account = xmppConnectionService.findAccountByJid(jid);
                if (account == null) {
                    account = new Account(jid.toBareJid(), password);
                    if (url.getHost().equals("spreedbox-demo.ddns.net")) {
                        account.setPort(5223);
                    }
                    else {
                        account.setPort(5222);
                    }
                    account.setHostname(url.getHost());
                    account.setOption(Account.OPTION_USETLS, true);
                    account.setOption(Account.OPTION_USECOMPRESSION, true);
                    account.setOption(Account.OPTION_REGISTER, false);
                    xmppConnectionService.createAccount(account);
                }
                else {
                    account.setPassword(password);
                    xmppConnectionService.updateAccount(account);
                }
            }
        }
    }
}