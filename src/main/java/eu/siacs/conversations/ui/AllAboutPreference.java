package eu.siacs.conversations.ui;

import android.content.Context;
import android.content.Intent;
import android.preference.Preference;
import android.util.AttributeSet;

import eu.siacs.conversations.utils.PhoneHelper;

public class AllAboutPreference extends Preference {
    public AllAboutPreference(final Context context, final AttributeSet attrs, final int defStyle) {
        super(context, attrs, defStyle);
        setSummary();
    }

    public AllAboutPreference(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        setSummary();
    }

    @Override
    protected void onClick() {
        super.onClick();
        final Intent intent = new Intent(getContext(), AllAboutActivity.class);
        getContext().startActivity(intent);
    }

    private void setSummary() {
        setSummary("Spreedbox " + PhoneHelper.getVersionName(getContext()));
    }
}

