package eu.siacs.conversations.ui;

import android.app.AlertDialog;
import android.app.FragmentManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.Toast;

import org.appspot.apprtc.*;

import java.security.KeyStoreException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import de.duenndns.ssl.MemorizingTrustManager;
import eu.siacs.conversations.Config;
import spreedbox.me.app.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.services.ExportLogsService;
import eu.siacs.conversations.xmpp.XmppConnection;
import eu.siacs.conversations.xmpp.jid.InvalidJidException;
import eu.siacs.conversations.xmpp.jid.Jid;

public class GlobalSettingsActivity extends AppCompatActivity {
    GlobalSettingsFragment settingsFragment;
    private Toolbar toolbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.global_settings_layout);


        toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle(R.string.settings);

        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });

        // Display the fragment as the main content.
        settingsFragment = new GlobalSettingsFragment();
        getFragmentManager()
                .beginTransaction()
                .replace(R.id.content, settingsFragment)
                .commit();
    }
}
