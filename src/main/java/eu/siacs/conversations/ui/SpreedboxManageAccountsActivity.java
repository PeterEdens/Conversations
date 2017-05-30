package eu.siacs.conversations.ui;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.owncloud.android.ui.activity.ManageAccountsActivity;

import java.util.List;

import eu.siacs.conversations.services.XmppConnectionService;

public class SpreedboxManageAccountsActivity extends ManageAccountsActivity {

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

    @Override
    public void performAccountRemoval(Account account) {
        SpreedboxAccountRemovalConfirmationDialog dialog = SpreedboxAccountRemovalConfirmationDialog.newInstance(account);
        mAccountName = account.name;
        dialog.show(getFragmentManager(), "dialog");
    }

    public static class SpreedboxAccountRemovalConfirmationDialog extends DialogFragment {

        private static final String ARG__ACCOUNT = "account";

        private Account mAccount;

        public static SpreedboxAccountRemovalConfirmationDialog newInstance(Account account) {
            Bundle bundle = new Bundle();
            bundle.putParcelable(ARG__ACCOUNT, account);

            SpreedboxAccountRemovalConfirmationDialog dialog = new SpreedboxAccountRemovalConfirmationDialog();
            dialog.setArguments(bundle);

            return dialog;
        }

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            mAccount = getArguments().getParcelable(ARG__ACCOUNT);
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return new AlertDialog.Builder(getActivity(), com.owncloud.android.R.style.Theme_ownCloud_Dialog)
                    .setTitle(com.owncloud.android.R.string.delete_account)
                    .setMessage(getResources().getString(com.owncloud.android.R.string.delete_account_warning, mAccount.name))
                    .setIcon(com.owncloud.android.R.drawable.ic_warning)
                    .setPositiveButton(com.owncloud.android.R.string.common_ok,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    AccountManager am = (AccountManager) getActivity().getSystemService(ACCOUNT_SERVICE);
                                    am.removeAccount(
                                            mAccount,
                                            (SpreedboxManageAccountsActivity)getActivity(),
                                            ((SpreedboxManageAccountsActivity)getActivity()).getHandler());

                                    List<eu.siacs.conversations.entities.Account> xmppAccounts = ((SpreedboxManageAccountsActivity)getActivity()).xmppConnectionService.getAccounts();
                                    for (eu.siacs.conversations.entities.Account acc: xmppAccounts) {
                                        if (acc.getJid().toBareJid().toString().equals(mAccount.name)) {
                                            ((SpreedboxManageAccountsActivity) getActivity()).xmppConnectionService.deleteAccount(acc);
                                            break;
                                        }
                                    }
                                }
                            })
                    .setNegativeButton(com.owncloud.android.R.string.common_cancel, null)
                    .create();
        }
    }
}