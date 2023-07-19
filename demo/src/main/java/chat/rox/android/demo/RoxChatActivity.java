package chat.rox.android.demo;

import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import android.view.MenuItem;
import android.widget.Toast;

import com.google.firebase.crashlytics.FirebaseCrashlytics;

import chat.rox.android.demo.util.RoxSessionDirector;
import chat.rox.android.sdk.FatalErrorHandler;
import chat.rox.android.sdk.Rox;
import chat.rox.android.sdk.RoxError;
import chat.rox.android.sdk.RoxSession;

public class RoxChatActivity extends AppCompatActivity implements FatalErrorHandler {
    public static final String EXTRA_SHOW_RATING_BAR_ON_STARTUP = "extra_show_rating_bar_on_startup";
    private static boolean active;
    private WidgetChatFragment fragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(null);

        setContentView(R.layout.activity_rox_chat);

        RoxSessionDirector
            .createSessionBuilderWithAnonymousVisitor(this, new RoxSessionDirector.OnSessionBuilderCreatedListener() {
                @Override
                public void onSessionBuilderCreated(Rox.SessionBuilder sessionBuilder) {
                    RoxSession session = sessionBuilder.setErrorHandler(RoxChatActivity.this).build();
                    initFragment(session);
                }

                @Override
                public void onError(RoxSessionDirector.SessionBuilderError error) {
                    Toast.makeText(RoxChatActivity.this, error.toString(), Toast.LENGTH_SHORT).show();
                }
            });
    }

    private void initFragment(RoxSession roxSession) {
        String fragmentByTag = WidgetChatFragment.class.getSimpleName();
        WidgetChatFragment currentFragment = (WidgetChatFragment) getSupportFragmentManager().findFragmentByTag(fragmentByTag);

        if (currentFragment == null) {
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
            currentFragment = new WidgetChatFragment();
            currentFragment.setRoxSession(roxSession);

            getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.roxChatContainer, currentFragment, fragmentByTag)
                .commit();
        }
        fragment = currentFragment;
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public void onError(@NonNull RoxError<FatalErrorType> error) {
        switch (error.getErrorType()) {
            case ACCOUNT_BLOCKED:
                showError(R.string.error_account_blocked_header,
                        R.string.error_account_blocked_desc);
                break;
            case VISITOR_BANNED:
                showError(R.string.error_user_banned_header, R.string.error_user_banned_desc);
                break;
            default:
                if (!BuildConfig.DEBUG) {
                    FirebaseCrashlytics.getInstance().recordException(
                        new Throwable("Handled unknown rox error: " + error.getErrorString()));
                }
                showError(R.string.error_unknown_header,
                    R.string.error_unknown_desc, error.getErrorString());
                break;
        }
    }

    private void showError(int errorHeaderId, int errorDescId, String... args) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.roxChatContainer,
                        ErrorFragment.newInstance(errorHeaderId, errorDescId, args))
                .commit();
    }

    @Override
    protected void onResume() {
        super.onResume();
        active = true; // It's singleTask activity
    }

    @Override
    protected void onPause() {
        super.onPause();
        active = false;
    }

    public static boolean isActive() {
        return active;
    }

    @Override
    public void onBackPressed() {
        fragment.onBackPressed();

        super.onBackPressed();
        overridePendingTransition(R.anim.pull_in_left, R.anim.push_out_right);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
