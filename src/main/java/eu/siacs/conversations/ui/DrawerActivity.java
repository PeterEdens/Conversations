package eu.siacs.conversations.ui;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.accounts.OperationCanceledException;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import com.example.sharedresourceslib.IconSpinnerAdapter;
import com.owncloud.android.MainApp;
import com.owncloud.android.authentication.AccountUtils;
import com.owncloud.android.datamodel.FileDataStorageManager;
import com.owncloud.android.datamodel.OCFile;
import com.owncloud.android.lib.common.OwnCloudAccount;
import com.owncloud.android.lib.common.operations.RemoteOperation;
import com.owncloud.android.lib.common.operations.RemoteOperationResult;
import com.owncloud.android.lib.common.utils.Log_OC;
import com.owncloud.android.lib.resources.users.RemoteGetUserQuotaOperation;
import com.owncloud.android.ui.TextDrawable;
import com.owncloud.android.ui.activity.BaseActivity;
import com.owncloud.android.ui.activity.ManageAccountsActivity;
import com.owncloud.android.utils.DisplayUtils;

import eu.siacs.conversations.entities.Presence;
import eu.siacs.conversations.xmpp.jid.InvalidJidException;
import eu.siacs.conversations.xmpp.jid.Jid;
import spreedbox.me.app.R;

import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP;

/**
 * Base class to handle setup of the drawer implementation including user switching and avatar fetching and fallback
 * generation.
 */
public abstract class DrawerActivity extends XmppActivity implements DisplayUtils.AvatarGenerationListener {
    private static final String TAG = DrawerActivity.class.getSimpleName();
    private static final String KEY_IS_ACCOUNT_CHOOSER_ACTIVE = "IS_ACCOUNT_CHOOSER_ACTIVE";
    private static final String KEY_CHECKED_MENU_ITEM = "CHECKED_MENU_ITEM";
    private static final int ACTION_MANAGE_ACCOUNTS = 101;
    private static final int MENU_ORDER_ACCOUNT = 1;
    private static final int MENU_ORDER_ACCOUNT_FUNCTION = 2;

    /**
     * Flag to signal that the activity will is finishing to enforce the creation of an ownCloud {@link Account}.
     */
    private boolean mRedirectingToSetupAccount = false;

    /**
     * Flag to signal when the value of mAccount was set.
     */
    protected boolean mAccountWasSet;

    /**
     * Flag to signal when the value of mAccount was restored from a saved state.
     */
    protected boolean mAccountWasRestored;

    /**
     * menu account avatar radius.
     */
    private float mMenuAccountAvatarRadiusDimension;

    /**
     * current account avatar radius.
     */
    private float mCurrentAccountAvatarRadiusDimension;

    /**
     * other accounts avatar radius.
     */
    private float mOtherAccountAvatarRadiusDimension;

    /**
     * Reference to the drawer layout.
     */
    private DrawerLayout mDrawerLayout;

    /**
     * Reference to the drawer toggle.
     */
    private ActionBarDrawerToggle mDrawerToggle;

    /**
     * Reference to the navigation view.
     */
    private NavigationView mNavigationView;

    /**
     * Reference to the account chooser toggle.
     */
    private ImageView mAccountChooserToggle;

    /**
     * Reference to the middle account avatar.
     */
    private ImageView mAccountMiddleAccountAvatar;

    /**
     * Reference to the end account avatar.
     */
    private ImageView mAccountEndAccountAvatar;

    /**
     * Flag to signal if the account chooser is active.
     */
    private boolean mIsAccountChooserActive;

    /**
     * Id of the checked menu item.
     */
    private int mCheckedMenuItem = Menu.NONE;

    /**
     * accounts for the (max) three displayed accounts in the drawer header.
     */
    private Account[] mAvatars = new Account[3];

    /**
     * container layout of the quota view.
     */
    private LinearLayout mQuotaView;

    /**
     * progress bar of the quota view.
     */
    private ProgressBar mQuotaProgressBar;

    /**
     * text view of the quota view.
     */
    private TextView mQuotaTextView;
    private FileDataStorageManager mStorageManager;
    private Account mCurrentAccount;
    private Spinner statusSpinner;
    private EditText messageEditText;

    /**
     * Initializes the drawer, its content and highlights the menu item with the given id.
     * This method needs to be called after the content view has been set.
     *
     * @param menuItemId the menu item to be checked/highlighted
     */
    protected void setupDrawer(int menuItemId) {
        setupDrawer();
        setDrawerMenuItemChecked(menuItemId);
    }

    @Override
    void onBackendConnected() {

        setupStatus(statusSpinner);
    }

    /**
     * Initializes the drawer and its content.
     * This method needs to be called after the content view has been set.
     */
    protected void setupDrawer() {
        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);

        mNavigationView = (NavigationView) findViewById(R.id.nav_view);
        if (mNavigationView != null) {
            setupDrawerHeader();

            setupDrawerMenu(mNavigationView);

            setupQuotaElement();
        }

        setupDrawerToggle();

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    /**
     * initializes and sets up the drawer toggle.
     */
    private void setupDrawerToggle() {
        mDrawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout, R.string.drawer_open, R.string.drawer_close) {

            /** Called when a drawer has settled in a completely closed state. */
            public void onDrawerClosed(View view) {
                super.onDrawerClosed(view);
                // standard behavior of drawer is to switch to the standard menu on closing
                if (mIsAccountChooserActive) {
                    toggleAccountList();
                }
                invalidateOptionsMenu();
            }

            /** Called when a drawer has settled in a completely open state. */
            public void onDrawerOpened(View drawerView) {
                super.onDrawerOpened(drawerView);
                mDrawerToggle.setDrawerIndicatorEnabled(true);
                invalidateOptionsMenu();
            }
        };

        // Set the drawer toggle as the DrawerListener
        mDrawerLayout.addDrawerListener(mDrawerToggle);
        mDrawerToggle.setDrawerIndicatorEnabled(true);
    }

    /**
     * initializes and sets up the drawer header.
     */
    private void setupDrawerHeader() {
        mAccountChooserToggle = (ImageView) findNavigationViewChildById(R.id.drawer_account_chooser_toogle);
        mAccountChooserToggle.setImageResource(R.drawable.ic_down);
        mIsAccountChooserActive = false;
        mAccountMiddleAccountAvatar = (ImageView) findNavigationViewChildById(R.id.drawer_account_middle);
        mAccountEndAccountAvatar = (ImageView) findNavigationViewChildById(R.id.drawer_account_end);
        Account current = AccountUtils.getCurrentOwnCloudAccount(this);
        if (current != null) {
            mCurrentAccount = current;
        }

        findNavigationViewChildById(R.id.drawer_active_user)
                .setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        toggleAccountList();
                    }
                });
    }

    /**
     * setup quota elements of the drawer.
     */
    private void setupQuotaElement() {
        mQuotaView = (LinearLayout) findViewById(R.id.drawer_quota);
        mQuotaProgressBar = (ProgressBar) findViewById(R.id.drawer_quota_ProgressBar);
        mQuotaTextView = (TextView) findViewById(R.id.drawer_quota_text);
        DisplayUtils.colorPreLollipopHorizontalProgressBar(mQuotaProgressBar);
    }

    private void executeChangePresence(Spinner spinner, int position) {
        Presence.Status status = getStatusFromSpinner(spinner);
        Account account = AccountUtils.getCurrentOwnCloudAccount(getApplicationContext());
        eu.siacs.conversations.entities.Account imAccount = null;

        if (account != null && xmppConnectionService != null) {
            try {
                imAccount = xmppConnectionService.findAccountByJid(Jid.fromString(account.name));
            } catch (InvalidJidException e) {
                e.printStackTrace();
            }
        }

        if (imAccount != null) {
            String statusMessage = messageEditText.getText().toString();
            PreferenceManager.getDefaultSharedPreferences(this).edit().putString(getString(R.string.pref_status_key), statusMessage);
            xmppConnectionService.changeStatus(imAccount, status, statusMessage, true);
        }


    }

    private Presence.Status getStatusFromSpinner(Spinner spinner) {
        switch (spinner.getSelectedItemPosition()) {
            case 0:
                return Presence.Status.CHAT;
            case 2:
                return Presence.Status.AWAY;
            case 3:
                return Presence.Status.XA;
            case 4:
                return Presence.Status.DND;
            default:
                return Presence.Status.ONLINE;
        }
    }

    private void setStatusInSpinner(Spinner spinner, Presence.Status status) {
        switch(status) {
            case AWAY:
                spinner.setSelection(2);
                break;
            case XA:
                spinner.setSelection(3);
                break;
            case CHAT:
                spinner.setSelection(0);
                break;
            case DND:
                spinner.setSelection(4);
                break;
            default:
                spinner.setSelection(1);
                break;
        }
    }

    void setupStatus(Spinner spinner) {
        Account account = AccountUtils.getCurrentOwnCloudAccount(getApplicationContext());
        eu.siacs.conversations.entities.Account imAccount = null;

        if (account != null) {
            try {
                imAccount = xmppConnectionService.findAccountByJid(Jid.fromString(account.name));
            } catch (InvalidJidException e) {
                e.printStackTrace();
            }
        }

        if (imAccount != null) {
            setStatusInSpinner(spinner, imAccount.getPresenceStatus());
            String message = imAccount.getPresenceStatusMessage();
            if (messageEditText.getText().length() == 0 && message != null) {
                messageEditText.append(message);
            }
        }
    }

    /**
     * setup drawer content, basically setting the item selected listener.
     *
     * @param navigationView the drawers navigation view
     */
    protected void setupDrawerMenu(final NavigationView navigationView) {
        // on pre lollipop the light theme adds a black tint to icons with white coloring
        // ruining the generic avatars, so tinting for icons is deactivated pre lollipop
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            //navigationView.setItemIconTintList(null);
        }

        messageEditText = (EditText) navigationView.getMenu().findItem(R.id.action_change_message).getActionView();
        messageEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int actionId, KeyEvent keyEvent) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    executeChangePresence(statusSpinner, statusSpinner.getSelectedItemPosition());
                    InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(textView.getWindowToken(), 0);
                    return true;
                }
                return false;
            }
        });

        LinearLayout layout = (LinearLayout) navigationView.getMenu().findItem(R.id.action_change_presence).getActionView();
        statusSpinner = (Spinner) layout.findViewById(R.id.online_status);
        final int[] icons = {
                R.drawable.ic_chat_black_24dp, // free to chat
                R.drawable.ic_check_circle_black_24dp, // online
                R.drawable.ic_schedule_black_24dp, // away
                R.drawable.ic_cancel_black_24dp, // not available
                R.drawable.ic_do_not_disturb_on_black_24dp // do not disturb
        };

        ArrayAdapter adapter = ArrayAdapter.createFromResource(this,
                R.array.presence_show_options,
                R.layout.simple_list_item);
        statusSpinner.setAdapter(adapter);
        statusSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                //
                try {
                    navigationView.getMenu().findItem(R.id.action_change_presence).setIcon(icons[position]);
                }
                catch (IllegalStateException e) {
                    e.printStackTrace();
                }
                executeChangePresence(statusSpinner, position);
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });


        // setup actions for drawer menu items
        navigationView.setNavigationItemSelectedListener(
                new NavigationView.OnNavigationItemSelectedListener() {
                    @Override
                    public boolean onNavigationItemSelected(MenuItem menuItem) {

                        mDrawerLayout.closeDrawers();

                        if (menuItem.getItemId() == com.owncloud.android.R.id.nav_share_files) {
                            String className = getString(com.owncloud.android.R.string.chooser_class);

                            if (className.length() != 0) {
                                Class<?> c = null;
                                try {
                                    c = Class.forName(className);
                                }
                                catch (ClassNotFoundException e) {
                                    e.printStackTrace();
                                }

                                if (c != null) {
                                    Intent chatIntent = new Intent(getApplicationContext(),
                                            c);
                                    chatIntent.putExtra(getString(com.owncloud.android.R.string.extra_mode), getString(R.string.mode_share_files));
                                    chatIntent.addFlags(FLAG_ACTIVITY_CLEAR_TOP);
                                    startActivity(chatIntent);
                                }
                            }
                        }
                        else if (menuItem.getItemId() == com.owncloud.android.R.id.nav_video_chat) {
                            String className = getString(com.owncloud.android.R.string.chooser_class);

                            if (className.length() != 0) {
                                Class<?> c = null;
                                try {
                                    c = Class.forName(className);
                                }
                                catch (ClassNotFoundException e) {
                                    e.printStackTrace();
                                }

                                if (c != null) {
                                    Intent chatIntent = new Intent(getApplicationContext(),
                                            c);
                                    chatIntent.putExtra(getString(com.owncloud.android.R.string.extra_mode), getString(com.owncloud.android.R.string.mode_video_chat));
                                    chatIntent.addFlags(FLAG_ACTIVITY_CLEAR_TOP);
                                    startActivity(chatIntent);
                                }
                            }
                        } else if (menuItem.getItemId() == R.id.action_settings) {
                            startActivity(new Intent(getApplicationContext(), SettingsActivity.class));
                        }
                        else if (menuItem.getItemId() == R.id.drawer_menu_account_manage) {
                                startActivity(new Intent(getApplicationContext(), ManageAccountsActivity.class));
                        }
                        else if (menuItem.getItemId() == R.id.drawer_menu_account_add) {
                            createAccount(false);
                        }
                        else if (menuItem.getItemId() == R.id.drawer_menu_contacts) {
                            startActivity(new Intent(getApplicationContext(), StartConversationActivity.class));
                        }
                        else if (menuItem.getItemId() == Menu.NONE) {
                            // account clicked
                            accountClicked(menuItem.getTitle().toString());
                        }
                        else {
                                Log_OC.i(TAG, "Unknown drawer menu item clicked: " + menuItem.getTitle());
                        }

                        return true;
                    }
                });


    }

    private void changePresence() {
        Intent intent;
        Account account = AccountUtils.getCurrentOwnCloudAccount(getApplicationContext());

        intent = new Intent(this, SetPresenceActivity.class);
        intent.putExtra(SetPresenceActivity.EXTRA_ACCOUNT, account.name);
        startActivity(intent);
    }

    /**
     * Tries to swap the current ownCloud {@link Account} for other valid and existing.
     *
     * If no valid ownCloud {@link Account} exists, the the user is requested
     * to create a new ownCloud {@link Account}.
     *
     * POSTCONDITION: updates {@link #mAccountWasSet} and {@link #mAccountWasRestored}.
     */
    protected void swapToDefaultAccount() {
        // default to the most recently used account
        Account newAccount = AccountUtils.getCurrentOwnCloudAccount(getApplicationContext());
        if (newAccount == null) {
            /// no account available: force account creation
            createAccount(true);
            mRedirectingToSetupAccount = true;
            mAccountWasSet = false;
            mAccountWasRestored = false;

        } else {
            mAccountWasSet = true;
            mAccountWasRestored = (newAccount.equals(mCurrentAccount));
            mCurrentAccount = newAccount;
        }
    }

    /**
     * Sets and validates the ownCloud {@link Account} associated to the Activity.
     *
     * If not valid, tries to swap it for other valid and existing ownCloud {@link Account}.
     *
     * POSTCONDITION: updates {@link #mAccountWasSet} and {@link #mAccountWasRestored}.
     *
     * @param account      New {@link Account} to set.
     * @param savedAccount When 'true', account was retrieved from a saved instance state.
     */
    protected void setAccount(Account account, boolean savedAccount) {
        Account oldAccount = mCurrentAccount;
        boolean validAccount =
                (account != null && AccountUtils.setCurrentOwnCloudAccount(getApplicationContext(),
                        account.name));
        if (validAccount) {
            mCurrentAccount = account;
            mAccountWasSet = true;
            mAccountWasRestored = (savedAccount || mCurrentAccount.equals(oldAccount));

        } else {
            swapToDefaultAccount();
        }
    }

    /**
     * Helper class handling a callback from the {@link AccountManager} after the creation of
     * a new ownCloud {@link Account} finished, successfully or not.
     */
    public class AccountCreationCallback implements AccountManagerCallback<Bundle> {

        boolean mMandatoryCreation;

        /**
         * Constuctor
         *
         * @param mandatoryCreation     When 'true', if an account was not created, the app is closed.
         */
        public AccountCreationCallback(boolean mandatoryCreation) {
            mMandatoryCreation = mandatoryCreation;
        }

        @Override
        public void run(AccountManagerFuture<Bundle> future) {
            DrawerActivity.this.mRedirectingToSetupAccount = false;
            boolean accountWasSet = false;
            if (future != null) {
                try {
                    Bundle result;
                    result = future.getResult();
                    String name = result.getString(AccountManager.KEY_ACCOUNT_NAME);
                    String type = result.getString(AccountManager.KEY_ACCOUNT_TYPE);
                    if (AccountUtils.setCurrentOwnCloudAccount(getApplicationContext(), name)) {
                        setAccount(new Account(name, type), false);
                        accountWasSet = true;
                    }

                    onAccountCreationSuccessful(future);
                } catch (OperationCanceledException e) {
                    Log_OC.d(TAG, "Account creation canceled");

                } catch (Exception e) {
                    Log_OC.e(TAG, "Account creation finished in exception: ", e);
                }

            } else {
                Log_OC.e(TAG, "Account creation callback with null bundle");
            }
            if (mMandatoryCreation && !accountWasSet) {
                moveTaskToBack(true);
            }
        }
    }

    protected void onAccountCreationSuccessful(AccountManagerFuture<Bundle> future) {
        updateAccountList();
    }

    /**
     * Launches the account creation activity.
     *
     * @param mandatoryCreation     When 'true', if an account is not created by the user, the app will be closed.
     *                              To use when no ownCloud account is available.
     */
    protected void createAccount(boolean mandatoryCreation) {
        AccountManager am = AccountManager.get(getApplicationContext());
        am.addAccount(MainApp.getAccountType(),
                null,
                null,
                null,
                this,
                new AccountCreationCallback(mandatoryCreation),
                new Handler());
    }

    /**
     * sets the new/current account and restarts. In case the given account equals the actual/current account the
     * call will be ignored.
     *
     * @param accountName The account name to be set
     */
    private void accountClicked(String accountName) {
        if (!AccountUtils.getCurrentOwnCloudAccount(getApplicationContext()).name.equals(accountName)) {
            AccountUtils.setCurrentOwnCloudAccount(getApplicationContext(), accountName);

        }
    }

    /**
     * click method for mini avatars in drawer header.
     *
     * @param view the clicked ImageView
     */
    public void onAccountDrawerClick(View view) {
        accountClicked(view.getContentDescription().toString());
    }

    /**
     * checks if the drawer exists and is opened.
     *
     * @return <code>true</code> if the drawer is open, else <code>false</code>
     */
    public boolean isDrawerOpen() {
        return mDrawerLayout != null && mDrawerLayout.isDrawerOpen(GravityCompat.START);
    }

    /**
     * closes the drawer.
     */
    public void closeDrawer() {
        if (mDrawerLayout != null) {
            mDrawerLayout.closeDrawer(GravityCompat.START);
        }
    }

    /**
     * opens the drawer.
     */
    public void openDrawer() {
        if (mDrawerLayout != null) {
            mDrawerLayout.openDrawer(GravityCompat.START);
        }
    }

    /**
     * Enable or disable interaction with all drawers.
     *
     * @param lockMode The new lock mode for the given drawer. One of {@link DrawerLayout#LOCK_MODE_UNLOCKED},
     *                 {@link DrawerLayout#LOCK_MODE_LOCKED_CLOSED} or {@link DrawerLayout#LOCK_MODE_LOCKED_OPEN}.
     */
    public void setDrawerLockMode(int lockMode) {
        if (mDrawerLayout != null) {
            mDrawerLayout.setDrawerLockMode(lockMode);
        }
    }

    /**
     * Enable or disable the drawer indicator.
     *
     * @param enable <code>true</code> to enable, <code>false</code> to disable
     */
    public void setDrawerIndicatorEnabled(boolean enable) {
        if (mDrawerToggle != null) {
            mDrawerToggle.setDrawerIndicatorEnabled(enable);
        }
    }

    /**
     * updates the account list in the drawer.
     */
    public void updateAccountList() {
        Account[] accounts = AccountManager.get(this).getAccountsByType(MainApp.getAccountType());
        if (mNavigationView != null && mDrawerLayout != null) {
            if (accounts.length > 0) {
                repopulateAccountList(accounts);
                setAccountInDrawer(AccountUtils.getCurrentOwnCloudAccount(this));
                populateDrawerOwnCloudAccounts();

                // activate second/end account avatar
                if (mAvatars[1] != null) {
                    DisplayUtils.setAvatar(mAvatars[1], this,
                            mOtherAccountAvatarRadiusDimension, getResources(), getStorageManager(),
                            findNavigationViewChildById(com.owncloud.android.R.id.drawer_account_end));
                    mAccountEndAccountAvatar.setVisibility(View.VISIBLE);
                } else {
                    mAccountEndAccountAvatar.setVisibility(View.GONE);
                }

                // activate third/middle account avatar
                if (mAvatars[2] != null) {
                    DisplayUtils.setAvatar(mAvatars[2], this,
                            mOtherAccountAvatarRadiusDimension, getResources(), getStorageManager(),
                            findNavigationViewChildById(com.owncloud.android.R.id.drawer_account_middle));
                    mAccountMiddleAccountAvatar.setVisibility(View.VISIBLE);
                } else {
                    mAccountMiddleAccountAvatar.setVisibility(View.GONE);
                }
            } else {
                mAccountEndAccountAvatar.setVisibility(View.GONE);
                mAccountMiddleAccountAvatar.setVisibility(View.GONE);
            }
        }
    }

    private FileDataStorageManager getStorageManager() {
        if (mStorageManager == null) {
            Account current = AccountUtils.getCurrentOwnCloudAccount(this);
            mStorageManager = new FileDataStorageManager(current, getContentResolver());
        }
        return mStorageManager;
    }

    /**
     * Getter for the ownCloud {@link Account} where the main {@link OCFile} handled by the activity
     * is located.
     *
     * @return OwnCloud {@link Account} where the main {@link OCFile} handled by the activity
     * is located.
     */
    public Account getAccount() {
        return mCurrentAccount;
    }

    /**
     * re-populates the account list.
     *
     * @param accounts list of accounts
     */
    private void repopulateAccountList(Account[] accounts) {
        // remove all accounts from list
        mNavigationView.getMenu().removeGroup(R.id.drawer_menu_accounts);

        // add all accounts to list
        for (int i = 0; i < accounts.length; i++) {
            try {
                // show all accounts except the currently active one
                if (!getAccount().name.equals(accounts[i].name)) {
                    MenuItem accountMenuItem = mNavigationView.getMenu().add(
                            R.id.drawer_menu_accounts,
                            Menu.NONE,
                            MENU_ORDER_ACCOUNT,
                            accounts[i].name)
                            .setIcon(TextDrawable.createAvatar(
                                    accounts[i].name,
                                    mMenuAccountAvatarRadiusDimension)
                            );
                    DisplayUtils.setAvatar(accounts[i], this, mMenuAccountAvatarRadiusDimension, getResources(), getStorageManager(), accountMenuItem);
                }
            } catch (Exception e) {
                Log_OC.e(TAG, "Error calculating RGB value for account menu item.", e);
                mNavigationView.getMenu().add(
                        R.id.drawer_menu_accounts,
                        Menu.NONE,
                        MENU_ORDER_ACCOUNT,
                        accounts[i].name)
                        .setIcon(R.drawable.ic_user);
            }
        }

        // re-add add-account and manage-accounts
        mNavigationView.getMenu().add(R.id.drawer_menu_accounts, R.id.drawer_menu_account_add,
                MENU_ORDER_ACCOUNT_FUNCTION,
                getResources().getString(R.string.prefs_add_account)).setIcon(R.drawable.ic_account_plus);
        mNavigationView.getMenu().add(R.id.drawer_menu_accounts, R.id.drawer_menu_account_manage,
                MENU_ORDER_ACCOUNT_FUNCTION,
                getResources().getString(R.string.drawer_manage_accounts)).setIcon(R.drawable.ic_settings);

        // adding sets menu group back to visible, so safety check and setting invisible
        showMenu();
    }

    /**
     * Updates title bar and home buttons (state and icon).
     * <p/>
     * Assumes that navigation drawer is NOT visible.
     */
    protected void updateActionBarTitleAndHomeButton(OCFile chosenFile) {

    }

    /**
     * sets the given account name in the drawer in case the drawer is available. The account name is shortened
     * beginning from the @-sign in the username.
     *
     * @param account the account to be set in the drawer
     */
    protected void setAccountInDrawer(Account account) {
        if (mDrawerLayout != null && account != null) {
            TextView username = (TextView) findNavigationViewChildById(R.id.drawer_username);
            TextView usernameFull = (TextView) findNavigationViewChildById(R.id.drawer_username_full);
            usernameFull.setText(account.name);
            try {
                OwnCloudAccount oca = new OwnCloudAccount(account, this);
                username.setText(oca.getDisplayName());
            } catch (com.owncloud.android.lib.common.accounts.AccountUtils.AccountNotFoundException e) {
                Log_OC.w(TAG, "Couldn't read display name of account fallback to account name");
                username.setText(AccountUtils.getAccountUsername(account.name));
            }


            DisplayUtils.setAvatar(account, this,
                    mCurrentAccountAvatarRadiusDimension, getResources(), getStorageManager(),
                    findNavigationViewChildById(R.id.drawer_current_account));

            // check and show quota info if available
            getAndDisplayUserQuota();
        }
    }

    /**
     * Toggle between standard menu and account list including saving the state.
     */
    private void toggleAccountList() {
        mIsAccountChooserActive = !mIsAccountChooserActive;
        showMenu();
    }

    /**
     * depending on the #mIsAccountChooserActive flag shows the account chooser or the standard menu.
     */
    private void showMenu() {
        if (mNavigationView != null) {
            if (mIsAccountChooserActive) {
                mAccountChooserToggle.setImageResource(R.drawable.ic_up);
                mNavigationView.getMenu().setGroupVisible(R.id.drawer_menu_accounts, true);
                mNavigationView.getMenu().setGroupVisible(R.id.drawer_menu_standard, false);
                mNavigationView.getMenu().setGroupVisible(R.id.drawer_menu_navigation, false);
            } else {
                mAccountChooserToggle.setImageResource(R.drawable.ic_down);
                mNavigationView.getMenu().setGroupVisible(R.id.drawer_menu_accounts, false);
                mNavigationView.getMenu().setGroupVisible(R.id.drawer_menu_standard, true);
                mNavigationView.getMenu().setGroupVisible(R.id.drawer_menu_navigation, true);
            }
        }
    }

    /**
     * shows or hides the quota UI elements.
     *
     * @param showQuota show/hide quota information
     */
    private void showQuota(boolean showQuota) {
        if (showQuota) {
            mQuotaView.setVisibility(View.VISIBLE);
        } else {
            mQuotaView.setVisibility(View.GONE);
        }
    }

    /**
     * configured the quota to be displayed.
     *
     * @param usedSpace the used space
     * @param totalSpace the total space
     * @param relative the percentage of space already used
     */
    private void setQuotaInformation(long usedSpace, long totalSpace, int relative) {
        mQuotaProgressBar.setProgress(relative);
        DisplayUtils.colorHorizontalProgressBar(mQuotaProgressBar, DisplayUtils.getRelativeInfoColor(this, relative));

        mQuotaTextView.setText(String.format(
                getString(R.string.drawer_quota),
                DisplayUtils.bytesToHumanReadable(usedSpace),
                DisplayUtils.bytesToHumanReadable(totalSpace)));

        showQuota(true);
    }

    /**
     * checks/highlights the provided menu item if the drawer has been initialized and the menu item exists.
     *
     * @param menuItemId the menu item to be highlighted
     */
    protected void setDrawerMenuItemChecked(int menuItemId) {
        if (mNavigationView != null && mNavigationView.getMenu() != null && mNavigationView.getMenu().findItem
                (menuItemId) != null) {
            mNavigationView.getMenu().findItem(menuItemId).setChecked(true);
            mCheckedMenuItem = menuItemId;
        } else {
            Log_OC.w(TAG, "setDrawerMenuItemChecked has been called with invalid menu-item-ID");
        }
    }

    /**
     * Retrieves and shows the user quota if available
     */
    private void getAndDisplayUserQuota() {
        // set user space information
        Thread t = new Thread(new Runnable() {
            public void run() {

                RemoteOperation getQuotaInfoOperation = new RemoteGetUserQuotaOperation();
                RemoteOperationResult result = getQuotaInfoOperation.execute(
                        AccountUtils.getCurrentOwnCloudAccount(DrawerActivity.this), DrawerActivity.this);

                if (result.isSuccess() && result.getData() != null) {
                    final RemoteGetUserQuotaOperation.Quota quota =
                            (RemoteGetUserQuotaOperation.Quota) result.getData().get(0);

                    final long used = quota.getUsed();
                    final long total = quota.getTotal();
                    final int relative = (int) Math.ceil(quota.getRelative());
                    final long quotaValue = quota.getQuota();

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (quotaValue > 0
                                    || quotaValue == RemoteGetUserQuotaOperation.QUOTA_LIMIT_INFO_NOT_AVAILABLE) {
                                /**
                                 * show quota in case
                                 * it is available and calculated (> 0) or
                                 * in case of legacy servers (==QUOTA_LIMIT_INFO_NOT_AVAILABLE)
                                 */
                                setQuotaInformation(used, total, relative);
                            } else {
                                /**
                                 * quotaValue < 0 means special cases like
                                 * {@link RemoteGetUserQuotaOperation.SPACE_NOT_COMPUTED},
                                 * {@link RemoteGetUserQuotaOperation.SPACE_UNKNOWN} or
                                 * {@link RemoteGetUserQuotaOperation.SPACE_UNLIMITED}
                                 * thus don't display any quota information.
                                 */
                                showQuota(false);
                            }
                        }
                    });
                }
            }
        });

        t.start();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            mIsAccountChooserActive = savedInstanceState.getBoolean(KEY_IS_ACCOUNT_CHOOSER_ACTIVE, false);
            mCheckedMenuItem = savedInstanceState.getInt(KEY_CHECKED_MENU_ITEM, Menu.NONE);
        }

        mCurrentAccountAvatarRadiusDimension = getResources()
                .getDimension(R.dimen.nav_drawer_header_avatar_radius);
        mOtherAccountAvatarRadiusDimension = getResources()
                .getDimension(R.dimen.nav_drawer_header_avatar_other_accounts_radius);
        mMenuAccountAvatarRadiusDimension = getResources()
                .getDimension(R.dimen.nav_drawer_menu_avatar_radius);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putBoolean(KEY_IS_ACCOUNT_CHOOSER_ACTIVE, mIsAccountChooserActive);
        outState.putInt(KEY_CHECKED_MENU_ITEM, mCheckedMenuItem);
    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);

        mIsAccountChooserActive = savedInstanceState.getBoolean(KEY_IS_ACCOUNT_CHOOSER_ACTIVE, false);
        mCheckedMenuItem = savedInstanceState.getInt(KEY_CHECKED_MENU_ITEM, Menu.NONE);

        // (re-)setup drawer state
        showMenu();

        // check/highlight the menu item if present
        if (mCheckedMenuItem > Menu.NONE || mCheckedMenuItem < Menu.NONE) {
            setDrawerMenuItemChecked(mCheckedMenuItem);
        }
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        // Sync the toggle state after onRestoreInstanceState has occurred.
        if (mDrawerToggle != null) {
            mDrawerToggle.syncState();
            if (isDrawerOpen()) {
                mDrawerToggle.setDrawerIndicatorEnabled(true);
            }
        }
        updateAccountList();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (mDrawerToggle != null) {
            mDrawerToggle.onConfigurationChanged(newConfig);
        }
    }

    @Override
    public void onBackPressed() {
        if (isDrawerOpen()) {
            closeDrawer();
            return;
        }
        super.onBackPressed();
    }

    @Override
    public void onResume() {
        super.onResume();
        setDrawerMenuItemChecked(mCheckedMenuItem);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // update Account list and active account if Manage Account activity replies with
        // - ACCOUNT_LIST_CHANGED = true
        // - RESULT_OK
        if (requestCode == ACTION_MANAGE_ACCOUNTS
                && resultCode == RESULT_OK
                && data.getBooleanExtra(ManageAccountsActivity.KEY_ACCOUNT_LIST_CHANGED, false)) {

            // current account has changed
            if (data.getBooleanExtra(ManageAccountsActivity.KEY_CURRENT_ACCOUNT_CHANGED, false)) {

            } else {
                updateAccountList();
            }
        }
    }

    /**
     * Finds a view that was identified by the id attribute from the drawer header.
     *
     * @param id the view's id
     * @return The view if found or <code>null</code> otherwise.
     */
    private View findNavigationViewChildById(int id) {
        return ((NavigationView) findViewById(R.id.nav_view)).getHeaderView(0).findViewById(id);
    }


    /**
     * populates the avatar drawer array with the first three ownCloud {@link Account}s while the first element is
     * always the current account.
     */
    private void populateDrawerOwnCloudAccounts() {
        mAvatars = new Account[3];
        Account[] accountsAll = AccountManager.get(this).getAccountsByType
                (MainApp.getAccountType());
        Account currentAccount = AccountUtils.getCurrentOwnCloudAccount(this);

        mAvatars[0] = currentAccount;
        int j = 0;
        for (int i = 1; i <= 2 && i < accountsAll.length && j < accountsAll.length; j++) {
            if (!currentAccount.equals(accountsAll[j])) {
                mAvatars[i] = accountsAll[j];
                i++;
            }
        }
    }



    /**
     * Adds other listeners to react on changes of the drawer layout.
     *
     * @param listener      Object interested in changes of the drawer layout.
     */
    public void addDrawerListener(DrawerLayout.DrawerListener listener) {
        if (mDrawerLayout != null) {
            mDrawerLayout.addDrawerListener(listener);
        } else {
            Log_OC.e(TAG, "Drawer layout not ready to add drawer listener");
        }
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
