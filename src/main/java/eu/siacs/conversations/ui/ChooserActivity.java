package eu.siacs.conversations.ui;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.os.Handler;
import android.util.Base64;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;

import com.owncloud.android.authentication.AccountUtils;
import com.owncloud.android.datamodel.FileDataStorageManager;
import com.owncloud.android.datamodel.ThumbnailsCacheManager;
import com.owncloud.android.ui.activity.FileDisplayActivity;
import com.owncloud.android.ui.activity.ManageAccountsActivity;
import com.owncloud.android.utils.DisplayUtils;

import org.appspot.apprtc.ConnectActivity;

import java.io.ByteArrayOutputStream;

import spreedbox.me.app.R;

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

    @Override
    public void onNewIntent(Intent intent) {
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent.hasExtra(EXTRA_MODE)) {
            String mode = intent.getStringExtra(EXTRA_MODE);
            if (mode.equals(MODE_IM)) {
                LaunchIM();
            }
            else if (mode.equals(MODE_VIDEO_CHAT)) {
                LaunchVideoChat();
            }
            else if (mode.equals(MODE_SHARE_FILES)) {
                LaunchShareFiles();
            }
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
                LaunchIM();
            }
        });

        findViewById(R.id.video_call_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                LaunchVideoChat();
            }
        });

        findViewById(R.id.files_button).setOnClickListener(new View.OnClickListener() {
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

        mAvatarContainer = (ImageView) findViewById(R.id.avatar_container);

        mAvatarContainer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(getApplicationContext(), ManageAccountsActivity.class);
                startActivity(intent);
            }
        });
        DisplayUtils.setAvatar(account, this,
                getResources()
                        .getDimension(com.owncloud.android.R.dimen.nav_drawer_header_avatar_radius), getResources(), getStorageManager(),
                mAvatarContainer);
    }

    private void LaunchShareFiles() {
        Intent fileDisplayIntent = new Intent(getApplicationContext(),
                FileDisplayActivity.class);
        startActivity(fileDisplayIntent);
    }

    private void LaunchVideoChat() {
        Intent connectActivity = new Intent(mContext, ConnectActivity.class);
        Account account = AccountUtils.getCurrentOwnCloudAccount(mContext);
        if (account != null) {
            AccountManager accountMgr = AccountManager.get(mContext);
            String serverUrl = accountMgr.getUserData(account, com.owncloud.android.lib.common.accounts.AccountUtils.Constants.KEY_OC_BASE_URL);
            String displayName = accountMgr.getUserData(account, com.owncloud.android.lib.common.accounts.AccountUtils.Constants.KEY_DISPLAY_NAME);
            connectActivity.putExtra(ConnectActivity.EXTRA_SERVERURL, serverUrl);
            connectActivity.putExtra(ConnectActivity.EXTRA_DISPLAYNAME, displayName);

            Bitmap avatar = null;
            if (mAvatarContainer != null) {
                avatar = DrawableToBitmap(mAvatarContainer.getDrawable());
            }
            else {
                avatar = ThumbnailsCacheManager.getBitmapFromDiskCache("a_" + account.name);
            }

            if (avatar != null) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                avatar.compress(Bitmap.CompressFormat.JPEG, 100, baos); //bm is the bitmap object
                byte[] byteArrayImage = baos.toByteArray();

                String encodedImage = Base64.encodeToString(byteArrayImage, Base64.DEFAULT);
                connectActivity.putExtra(ConnectActivity.EXTRA_AVATAR, encodedImage);
            }
        }
        startActivity(connectActivity);
    }

    private void LaunchIM() {
        Intent startConversationActivity = new Intent(mContext, StartConversationActivity.class);
        startConversationActivity.putExtra("init", true);
        startActivity(startConversationActivity);
    }

    private Bitmap DrawableToBitmap(Drawable drawable) {
        Bitmap bitmap = null;

        if (drawable instanceof BitmapDrawable) {
            BitmapDrawable bitmapDrawable = (BitmapDrawable) drawable;
            if(bitmapDrawable.getBitmap() != null) {
                return bitmapDrawable.getBitmap();
            }
        }

        if(drawable.getIntrinsicWidth() <= 0 || drawable.getIntrinsicHeight() <= 0) {
            bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888); // Single color bitmap will be created of 1x1 pixel
        } else {
            bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        }

        Canvas canvas = new Canvas(bitmap);
        Paint paint2 = new Paint();
        paint2.setColor(Color.WHITE);
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
