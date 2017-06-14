package org.wordpress.android.ui.accounts.login;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.TextInputLayout;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.wordpress.android.R;
import org.wordpress.android.WordPress;
import org.wordpress.android.fluxc.Dispatcher;
import org.wordpress.android.fluxc.generated.SiteActionBuilder;
import org.wordpress.android.fluxc.store.AccountStore;
import org.wordpress.android.fluxc.store.SiteStore;
import org.wordpress.android.util.AppLog;
import org.wordpress.android.util.AppLog.T;
import org.wordpress.android.util.EditTextUtils;
import org.wordpress.android.util.NetworkUtils;
import org.wordpress.android.util.ToastUtils;

import javax.inject.Inject;

public class LoginUsernamePasswordFragment extends Fragment implements TextWatcher {
    private static final String KEY_IN_PROGRESS = "KEY_IN_PROGRESS";
    private static final String KEY_LOGIN_FINISHED = "KEY_LOGIN_FINISHED";
    private static final String KEY_REQUESTED_USERNAME = "KEY_REQUESTED_USERNAME";

    private static final String ARG_SITE_ADDRESS = "ARG_SITE_ADDRESS";

    public static final String TAG = "login_username_password_fragment_tag";

    private TextInputLayout mUsernameEditTextLayout;
    private TextInputLayout mPasswordEditTextLayout;

    private EditText mUsernameEditText;
    private EditText mPasswordEditText;
    private Button mNextButton;
    private ProgressDialog mProgressDialog;

    private LoginListener mLoginListener;

    private boolean mInProgress;
    private boolean mAuthFailed;
    private boolean mLoginFinished;

    private String mRequestedUsername;

    private String mSiteAddress;

    @Inject Dispatcher mDispatcher;

    public static LoginUsernamePasswordFragment newInstance(String siteAddress) {
        LoginUsernamePasswordFragment fragment = new LoginUsernamePasswordFragment();
        Bundle args = new Bundle();
        args.putString(ARG_SITE_ADDRESS, siteAddress);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ((WordPress) getActivity().getApplication()).component().inject(this);

        mSiteAddress = getArguments().getString(ARG_SITE_ADDRESS);

        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        ViewGroup rootView = (ViewGroup) inflater.inflate(R.layout.login_username_password_screen, container, false);

        mUsernameEditText = (EditText) rootView.findViewById(R.id.login_username);
        mUsernameEditText.addTextChangedListener(this);

        mPasswordEditText = (EditText) rootView.findViewById(R.id.login_password);
        mPasswordEditText.addTextChangedListener(this);

        mUsernameEditTextLayout = (TextInputLayout) rootView.findViewById(R.id.login_username_layout);
        mPasswordEditTextLayout = (TextInputLayout) rootView.findViewById(R.id.login_password_layout);

        mNextButton = (Button) rootView.findViewById(R.id.login_username_password_next_button);
        mNextButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                next();
            }
        });

        mUsernameEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (event != null
                        && event.getAction() == KeyEvent.ACTION_UP
                        && event.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
                    mPasswordEditText.requestFocus();
                }

                // always consume the event so the focus stays in the EditText
                return true;
            }
        });

        mPasswordEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (event != null
                        && event.getAction() == KeyEvent.ACTION_UP
                        && event.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
                    next();
                }

                // always consume the event so the focus stays in the EditText
                return true;
            }
        });

        rootView.findViewById(R.id.login_lost_password).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mLoginListener != null) {
                    mLoginListener.forgotPassword();
                }
            }
        });

        return rootView;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Toolbar toolbar = (Toolbar) view.findViewById(R.id.toolbar);
        ((AppCompatActivity) getActivity()).setSupportActionBar(toolbar);

        ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowTitleEnabled(false);
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        if (savedInstanceState == null) {
            EditTextUtils.showSoftInput(mUsernameEditText);
        }
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        if (savedInstanceState != null) {
            mInProgress = savedInstanceState.getBoolean(KEY_IN_PROGRESS);
            mLoginFinished = savedInstanceState.getBoolean(KEY_LOGIN_FINISHED);

            mRequestedUsername = savedInstanceState.getString(KEY_REQUESTED_USERNAME);

            if (mInProgress) {
                showProgressDialog();
            }
        }
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof LoginListener) {
            mLoginListener = (LoginListener) context;
        } else {
            throw new RuntimeException(context.toString() + " must implement LoginListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mLoginListener = null;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putBoolean(KEY_IN_PROGRESS, mInProgress);
        outState.putBoolean(KEY_LOGIN_FINISHED, mLoginFinished);
        outState.putString(KEY_REQUESTED_USERNAME, mRequestedUsername);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_login, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.help) {
            mLoginListener.help();
            return true;
        }

        return false;
    }

    protected void next() {
        if (!NetworkUtils.checkConnection(getActivity())) {
            return;
        }

        showProgressDialog();

        mRequestedUsername = mUsernameEditText.getText().toString();
        String password = mPasswordEditText.getText().toString();

        // clear up the authentication-failed flag before
        mAuthFailed = false;

        SiteStore.RefreshSitesXMLRPCPayload selfHostedPayload = new SiteStore.RefreshSitesXMLRPCPayload();
        selfHostedPayload.username = mRequestedUsername;
        selfHostedPayload.password = password;
        selfHostedPayload.url = mSiteAddress;
        mDispatcher.dispatch(SiteActionBuilder.newFetchSitesXmlRpcAction(selfHostedPayload));
    }

    private String getCleanedUsername() {
        return EditTextUtils.getText(mUsernameEditText).trim();
    }

    @Override
    public void afterTextChanged(Editable s) {
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        updateNextButton();
        mUsernameEditTextLayout.setError(null);
        mPasswordEditTextLayout.setError(null);
    }

    private void updateNextButton() {
        mNextButton.setEnabled(getCleanedUsername().length() > 0 && mPasswordEditText.getText().length() > 0);
    }

    private void showError(String errorMessage) {
        mUsernameEditTextLayout.setError(" ");
        mPasswordEditTextLayout.setError(errorMessage);
    }

    @Override
    public void onStart() {
        super.onStart();
        mDispatcher.register(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        mDispatcher.unregister(this);
    }

    private void showProgressDialog() {
        mNextButton.setEnabled(false);
        mProgressDialog =
                ProgressDialog.show(getActivity(), "", getActivity().getString(R.string.logging_in), true, true,
                        new DialogInterface.OnCancelListener() {
                            @Override
                            public void onCancel(DialogInterface dialogInterface) {
                                if (mInProgress) {
                                    endProgress();
                                }
                            }
                        });
        mInProgress = true;
    }

    private void endProgress() {
        mInProgress = false;

        if (mProgressDialog != null) {
            mProgressDialog.cancel();
            mProgressDialog = null;
        }

        mRequestedUsername = null;

        updateNextButton();
    }

    private void handleAuthError(AccountStore.AuthenticationErrorType error, String errorMessage) {
        switch (error) {
            case INCORRECT_USERNAME_OR_PASSWORD:
            case NOT_AUTHENTICATED: // NOT_AUTHENTICATED is the generic error from XMLRPC response on first call.
                showError(getString(R.string.username_or_password_incorrect));
                break;
            case INVALID_OTP:
            case INVALID_TOKEN:
            case AUTHORIZATION_REQUIRED:
            case NEEDS_2FA:
                showError("2FA not supported for self-hosted sites. Please use an app-password.");
                break;
            default:
                AppLog.e(T.NUX, "Server response: " + errorMessage);

                ToastUtils.showToast(getActivity(),
                        errorMessage == null ? getString(R.string.error_generic) : errorMessage);
                break;
        }
    }

    // OnChanged events

    @SuppressWarnings("unused")
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onAuthenticationChanged(AccountStore.OnAuthenticationChanged event) {
        // only emitted when the login failed (but not when succeeded)

        if (!isAdded() || mLoginFinished || mRequestedUsername == null) {
            // just bail
            return;
        }

        if (event.isError()) {
            AppLog.e(T.API, "Login with username/pass onAuthenticationChanged has error: " + event.error.type + " - " +
                    event.error.message);

            mAuthFailed = true;

            handleAuthError(event.error.type, event.error.message);
        }
    }

    @SuppressWarnings("unused")
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onSiteChanged(SiteStore.OnSiteChanged event) {
        if (!isAdded() || mLoginFinished) {
            return;
        }

        if (event.isError()) {
            if (mRequestedUsername == null) {
                // just bail since the operation was cancelled
                return;
            }

            endProgress();

            String errorMessage = event.error.toString();
            AppLog.e(T.API, "Login with username/pass onSiteChanged has error: " + event.error.type + " - " +
                    errorMessage);

            if (!mAuthFailed) {
                // show the error if not already displayed in onAuthenticationChanged (like in username/pass error)
                showError(errorMessage);
            }

            return;
        }

        // continue with success, even if the operation was cancelled since the user got logged in regardless. So, go on
        //  with finishing the login process

        endProgress();

        // mark as finished so any subsequent onSiteChanged (e.g. triggered by WPMainActivity) won't be intercepted
        mLoginFinished = true;

        if (mLoginListener != null) {
            mLoginListener.loggedInViaUsernamePassword();
        }
    }
}
