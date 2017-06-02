package eu.siacs.conversations.ui;

import android.Manifest;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.os.Handler;
import android.util.Base64;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.owncloud.android.authentication.AccountUtils;
import com.owncloud.android.datamodel.FileDataStorageManager;
import com.owncloud.android.datamodel.ThumbnailsCacheManager;
import com.owncloud.android.ui.activity.FileDisplayActivity;
import com.owncloud.android.ui.activity.ManageAccountsActivity;
import com.owncloud.android.utils.DisplayUtils;

import org.appspot.apprtc.ConnectActivity;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xmpp.jid.InvalidJidException;
import eu.siacs.conversations.xmpp.jid.Jid;
import spreedbox.me.app.R;

import static com.example.sharedresourceslib.BroadcastTypes.ACTION_PRESENCE_CHANGED;
import static com.example.sharedresourceslib.BroadcastTypes.EXTRA_ACCOUNT_NAME;
import static com.example.sharedresourceslib.BroadcastTypes.EXTRA_PRESENCE;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
public class ChooserActivity extends AppCompatActivity implements DisplayUtils.AvatarGenerationListener {
    public static final String EXTRA_MODE = "eu.siacs.conversations.ui.EXTRA_MODE";
    public static final String MODE_IM = "eu.siacs.conversations.ui.MODE_IM";
    public static final String MODE_VIDEO_CHAT = "eu.siacs.conversations.ui.MODE_VIDEO_CHAT";
    public static final String MODE_SHARE_FILES = "eu.siacs.conversations.ui.MODE_SHARE_FILES";

    private FileDataStorageManager mStorageManager;
    private ImageView mAvatarContainer;
    private Context mContext;
    public XmppConnectionService xmppConnectionService;
    public boolean xmppConnectionServiceBound = false;
    private Account mAccount;

    @Override
    public void onNewIntent(Intent intent) {
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent.hasExtra(EXTRA_MODE)) {
            String mode = intent.getStringExtra(EXTRA_MODE);
            if (mode.equals(MODE_IM)) {
                LaunchIM();
            } else if (mode.equals(MODE_VIDEO_CHAT)) {
                LaunchVideoChat();
            } else if (mode.equals(MODE_SHARE_FILES)) {
                LaunchShareFiles();
            }
        }
    }

    protected ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            XmppConnectionService.XmppConnectionBinder binder = (XmppConnectionService.XmppConnectionBinder) service;
            xmppConnectionService = binder.getService();
            xmppConnectionServiceBound = true;
            updateUnread();
            updateStatus();
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

    void logout() {

        AccountManager am = (AccountManager) getSystemService(ACCOUNT_SERVICE);
        // loop through all accounts to remove them
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.GET_ACCOUNTS) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        Account[] accounts = am.getAccounts();
        for (int index = 0; index < accounts.length; index++) {
            if (accounts[index].type.equals(getString(R.string.account_type)))
                am.removeAccount(accounts[index], null, null);
        }

        List<eu.siacs.conversations.entities.Account> xmppAccounts = new ArrayList<eu.siacs.conversations.entities.Account>(xmppConnectionService.getAccounts());
        for (eu.siacs.conversations.entities.Account acc: xmppAccounts) {
            xmppConnectionService.deleteAccount(acc);
        }
        finish();
    }

    private void updateStatus() {
        eu.siacs.conversations.entities.Account imAccount = null;

        if (mAccount != null) {
            try {
                imAccount = xmppConnectionService.findAccountByJid(Jid.fromString(mAccount.name));
            } catch (InvalidJidException e) {
                e.printStackTrace();
            }
        }

        if (imAccount != null) {
            Intent broadcastIntent = new Intent();
            broadcastIntent.setAction(ACTION_PRESENCE_CHANGED);
            broadcastIntent.putExtra(EXTRA_PRESENCE, imAccount.getPresenceStatus().toShowString());
            broadcastIntent.putExtra(EXTRA_ACCOUNT_NAME, mAccount.name);
            sendBroadcast(broadcastIntent);
        }
    }

    private void updateUnread() {
        if (xmppConnectionService == null) {
            return;
        }

        TextView imSublabel = (TextView) findViewById(R.id.chat_sublabel);
        if (imSublabel != null) {
            String messageTxt = "";

            int unread = xmppConnectionService.unreadCount();
            if (unread == 0) {
                messageTxt = getString(R.string.no_new_messages);
            } else if (unread == 1) {
                messageTxt = getString(R.string.unread_message);
            } else {
                messageTxt = String.format(getString(R.string.unread_messages), unread);
            }
            imSublabel.setText(messageTxt);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mContext = this;

        Intent intent = getIntent();

        if (intent != null) {
            handleIntent(intent);
        }

        mAccount = AccountUtils.getCurrentOwnCloudAccount(this);

        if (mAccount == null) {

            Intent authIntent = new Intent(this, SpreedboxAuthenticatorActivity.class);
            String serverAddress = PreferenceManager.getDefaultSharedPreferences(this).getString("last_server", "");
            if (serverAddress.length() != 0) {
                authIntent.setData(Uri.parse("cloud://login/server:" + serverAddress));
            }
            startActivity(authIntent);
        }

        if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean("use_dark_chooser", true)) {
            setContentView(R.layout.activity_chooser_dark);
        }
        else {
            setContentView(R.layout.activity_chooser);
        }

        // Hide UI first
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }

        if (mAccount != null) {
            TextView sublabel = (TextView) findViewById(R.id.avatar_sublabel);
            sublabel.setText(mAccount.name);
        }
        updateUnread();

        findViewById(R.id.logout_layout).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                new AlertDialog.Builder(mContext)
                        .setTitle(R.string.logout)
                        .setMessage(R.string.logout_confirm)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {

                            public void onClick(DialogInterface dialog, int whichButton) {
                                logout();
                            }})
                        .setNegativeButton(android.R.string.no, null).show();

            }
        });

        findViewById(R.id.logout_label).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                new AlertDialog.Builder(mContext)
                        .setTitle(R.string.logout)
                        .setMessage(R.string.logout_confirm)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {

                            public void onClick(DialogInterface dialog, int whichButton) {
                                logout();
                            }})
                        .setNegativeButton(android.R.string.no, null).show();

            }
        });

        findViewById(R.id.chat_controls).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                LaunchIM();
            }
        });

        findViewById(R.id.secure_chat_label).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                LaunchIM();
            }
        });

        findViewById(R.id.video_call_controls).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                LaunchVideoChat();
            }
        });

        findViewById(R.id.video_call_label).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                LaunchVideoChat();
            }
        });

        findViewById(R.id.files_controls).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                LaunchShareFiles();
            }
        });

        findViewById(R.id.files_label).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                LaunchShareFiles();
            }
        });

        findViewById(R.id.settings_controls).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent globalSettingsIntent = new Intent(getApplicationContext(), GlobalSettingsActivity.class);
                startActivity(globalSettingsIntent);
            }
        });

        findViewById(R.id.settings_label).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent globalSettingsIntent = new Intent(getApplicationContext(), GlobalSettingsActivity.class);
                startActivity(globalSettingsIntent);
            }
        });

        mAvatarContainer = (ImageView) findViewById(R.id.avatar_container);

        mAvatarContainer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(getApplicationContext(), SpreedboxManageAccountsActivity.class);
                startActivity(intent);
            }
        });

        TextView avatarLabel = (TextView)findViewById(R.id.avatar_label);
        avatarLabel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(getApplicationContext(), SpreedboxManageAccountsActivity.class);
                startActivity(intent);
            }
        });

        if (mAccount != null) {
            DisplayUtils.setAvatar(mAccount, this,
                    getResources()
                            .getDimension(R.dimen.chooser_avatar_radius), getResources(), getStorageManager(),
                    mAvatarContainer);
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        updateUnread();

        // Check if the account has been updated
        mAccount = AccountUtils.getCurrentOwnCloudAccount(this);
        if (mAccount != null) {
            DisplayUtils.setAvatar(mAccount, this,
                    getResources()
                            .getDimension(R.dimen.chooser_avatar_radius), getResources(), getStorageManager(),
                    mAvatarContainer);
        }
    }

    private void LaunchShareFiles() {
        Intent fileDisplayIntent = new Intent(getApplicationContext(),
                FileDisplayActivity.class);
        startActivity(fileDisplayIntent);
    }

    private void LaunchVideoChat() {
        Intent connectActivity = new Intent(mContext, ConnectActivity.class);

        /*Account account = AccountUtils.getCurrentOwnCloudAccount(mContext);
        if (account != null) {
            AccountManager accountMgr = AccountManager.get(mContext);
            String serverUrl = accountMgr.getUserData(account, com.owncloud.android.lib.common.accounts.AccountUtils.Constants.KEY_OC_BASE_URL);
            String displayName = accountMgr.getUserData(account, com.owncloud.android.lib.common.accounts.AccountUtils.Constants.KEY_DISPLAY_NAME);
            connectActivity.putExtra(ConnectActivity.EXTRA_SERVERURL, serverUrl);
            connectActivity.putExtra(ConnectActivity.EXTRA_DISPLAYNAME, displayName);

            Bitmap avatar = null;
            if (mAvatarContainer != null) {
                avatar = DrawableToBitmap(mAvatarContainer, mAvatarContainer.getDrawable());
            }
            else {
                avatar = ThumbnailsCacheManager.getBitmapFromDiskCache("squ_" + account.name);
            }

            if (avatar != null) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                avatar.compress(Bitmap.CompressFormat.PNG, 100, baos); //bm is the bitmap object
                byte[] byteArrayImage = baos.toByteArray();

                String encodedImage = Base64.encodeToString(byteArrayImage, Base64.DEFAULT);
                connectActivity.putExtra(ConnectActivity.EXTRA_AVATAR, encodedImage);
            }
        }*/
        startActivity(connectActivity);
    }

    private void LaunchIM() {
        Intent startConversationActivity = new Intent(mContext, StartConversationActivity.class);
        startConversationActivity.putExtra("init", true);
        startActivity(startConversationActivity);
    }

    private Bitmap DrawableToBitmap(ImageView container, Drawable drawable) {
        Bitmap bitmap = null;

        if (drawable instanceof BitmapDrawable) {
            BitmapDrawable bitmapDrawable = (BitmapDrawable) drawable;
            if(bitmapDrawable.getBitmap() != null) {
                return bitmapDrawable.getBitmap();
            }
        }

        if(drawable.getIntrinsicWidth() <= 0 || drawable.getIntrinsicHeight() <= 0) {
            bitmap = Bitmap.createBitmap(container.getWidth(), container.getHeight(), Bitmap.Config.ARGB_8888); // Single color bitmap will be created of 1x1 pixel
        } else {
            bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        }

        Canvas canvas = new Canvas(bitmap);
        Paint paint2 = new Paint();
        paint2.setColor(Color.TRANSPARENT);
        paint2.setStyle(Paint.Style.FILL);
        canvas.drawPaint(paint2);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bitmap;

    }

    private FileDataStorageManager getStorageManager() {
        if (mStorageManager == null) {
            Account current = AccountUtils.getCurrentOwnCloudAccount(this);
            mStorageManager = new FileDataStorageManager(current, getContentResolver());
        }
        return mStorageManager;
    }

    @Override
    public void avatarGenerated(Drawable avatarDrawable, Object callContext) {
        if (callContext instanceof MenuItem) {
            MenuItem mi = (MenuItem)callContext;
            mi.setIcon(avatarDrawable);
        } else if (callContext instanceof ImageView) {
            ImageView iv = (ImageView)callContext;
            iv.setImageDrawable(avatarDrawable);
        }
    }

    @Override
    public boolean shouldCallGeneratedCallback(String tag, Object callContext) {
        if (callContext instanceof MenuItem) {
            MenuItem mi = (MenuItem)callContext;
            return String.valueOf(mi.getTitle()).equals(tag);
        } else if (callContext instanceof ImageView) {
            ImageView iv = (ImageView)callContext;
            return String.valueOf(iv.getTag()).equals(tag);
        }
        return false;
    }
}
