package io.freeword.activities;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.ResultReceiver;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import android.widget.TextView.OnEditorActionListener;
import info.guardianproject.cacheword.CacheWordHandler;
import info.guardianproject.cacheword.ICacheWordSubscriber;
import io.freeword.R;
import io.freeword.util.PassphraseUtil;

import java.security.GeneralSecurityException;

public class LockScreenActivity extends Activity implements ICacheWordSubscriber {

    private CacheWordHandler cacheWordHandler;

    private String passwordError;

    private EditText enterPassphraseEditText;
    private EditText newPassphraseEditText;
    private EditText confirmNewPassphraseEditText;

    private View createPassphraseView;
    private View enterPassphraseView;

    private Button openButton;

    private TwoViewSlider twoViewSlider;

    private static final String TAG = LockScreenActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        cacheWordHandler = new CacheWordHandler(this);
        cacheWordHandler.connectToService();

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        }

        setContentView(R.layout.activity_lock_screen);

        createPassphraseView = findViewById(R.id.llCreatePassphrase);
        enterPassphraseView = findViewById(R.id.llEnterPassphrase);

        newPassphraseEditText = (EditText) findViewById(R.id.editNewPassphrase);
        confirmNewPassphraseEditText = (EditText) findViewById(R.id.editConfirmNewPassphrase);

        enterPassphraseEditText = (EditText) findViewById(R.id.editEnterPassphrase);

        ViewFlipper vf = (ViewFlipper) findViewById(R.id.viewFlipper1);
        LinearLayout flipView1 = (LinearLayout) findViewById(R.id.flipView1);
        LinearLayout flipView2 = (LinearLayout) findViewById(R.id.flipView2);

        twoViewSlider = new TwoViewSlider(vf, flipView1, flipView2, newPassphraseEditText, confirmNewPassphraseEditText);
    }

    @Override
    protected void onPause() {
        super.onPause();
        cacheWordHandler.disconnectFromService();
    }

    @Override
    protected void onResume() {
        super.onResume();
        cacheWordHandler.connectToService();
    }

    @Override
    public void onCacheWordUninitialized() {
        initializePassphrase();
    }

    @Override
    public void onCacheWordLocked() {
        promptPassphrase();
    }

    @Override
    public void onCacheWordOpened() {
        Intent intent = new Intent(getApplicationContext(), NotesListActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);

        cacheWordHandler.disconnectFromService();
        finish();
    }

    private boolean newEqualsConfirmation() {
        return newPassphraseEditText.getText().toString()
                .equals(confirmNewPassphraseEditText.getText().toString());
    }

    private void showValidationError() {
        Toast.makeText(LockScreenActivity.this, passwordError, Toast.LENGTH_LONG).show();
        newPassphraseEditText.requestFocus();
    }

    private void showInequalityError() {
        Toast.makeText(LockScreenActivity.this,
                       R.string.lock_screen_passphrases_not_matching,
                       Toast.LENGTH_SHORT).show();
        clearNewFields();
    }

    private void clearNewFields() {
        newPassphraseEditText.getEditableText().clear();
        confirmNewPassphraseEditText.getEditableText().clear();
    }

    private boolean isPasswordValid() {
    	boolean valid = PassphraseUtil.validatePassphrase(newPassphraseEditText.getText().toString().toCharArray());
    	if(!valid) {
            passwordError = getString(R.string.pass_err_length);
        }
        return valid;
    }

    private boolean isConfirmationFieldEmpty() {
        return confirmNewPassphraseEditText.getText().toString().length() == 0;
    }

    private void initializePassphrase() {
        createPassphraseView.setVisibility(View.VISIBLE);
        enterPassphraseView.setVisibility(View.GONE);

        newPassphraseEditText.setOnEditorActionListener(new OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_NULL || actionId == EditorInfo.IME_ACTION_DONE) {
                    if (!isPasswordValid()) {
                        showValidationError();
                    }
                    else {
                        twoViewSlider.showConfirmationField();
                    }
                }
                return false;
            }
        });

        confirmNewPassphraseEditText.setOnEditorActionListener(new OnEditorActionListener() {

            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_NULL || actionId == EditorInfo.IME_ACTION_DONE) {
                    if (!newEqualsConfirmation()) {
                        showInequalityError();
                        twoViewSlider.showNewPasswordField();
                    }
                }
                return false;
            }
        });

        Button createPassphraseButton = (Button) findViewById(R.id.btnCreate);
        createPassphraseButton.setOnClickListener(new OnClickListener()
        {
            @Override
            public void onClick(View v) {
                if (!isPasswordValid()) {
                    showValidationError();
                    twoViewSlider.showNewPasswordField();
                }
                else if (isConfirmationFieldEmpty()) {
                    twoViewSlider.showConfirmationField();
                }
                else if (!newEqualsConfirmation()) {
                    showInequalityError();
                    twoViewSlider.showNewPasswordField();
                }
                else {
                    try {
                        cacheWordHandler.setPassphrase(newPassphraseEditText.getText().toString().toCharArray());
                    }
                    catch (GeneralSecurityException e) {
                        Log.e(TAG, "Cacheword pass initialization failed: " + e.getMessage());
                    }
                }
            }
        });
    }

    private void promptPassphrase() {
        createPassphraseView.setVisibility(View.GONE);
        enterPassphraseView.setVisibility(View.VISIBLE);

        openButton = (Button) findViewById(R.id.btnOpen);
        openButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (enterPassphraseEditText.getText().toString().length() == 0) {
                    return;
                }
                try {
                    // Check the passphrase entered by the user
                    cacheWordHandler.setPassphrase(enterPassphraseEditText.getText().toString().toCharArray());
                }
                catch (GeneralSecurityException e) {
                    enterPassphraseEditText.setText("");
                    Toast.makeText(LockScreenActivity.this,
                                   R.string.lock_screen_passphrase_incorrect,
                                   Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Cacheword pass verification failed: " + e.getMessage());
                }
            }
        });

        enterPassphraseEditText.setOnEditorActionListener(new OnEditorActionListener()
        {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event)
            {
                if (actionId == EditorInfo.IME_NULL || actionId == EditorInfo.IME_ACTION_GO)
                {
                    InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                    Handler threadHandler = new Handler();
                    imm.hideSoftInputFromWindow(v.getWindowToken(), 0, new ResultReceiver(threadHandler) {
                        @Override
                        protected void onReceiveResult(int resultCode, Bundle resultData) {
                            super.onReceiveResult(resultCode, resultData);
                            openButton.performClick();
                            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
                        }
                    });
                    return true;
                }
                return false;
            }
        });
    }

    private class TwoViewSlider {

        private boolean firstIsShown = true;
        private ViewFlipper flipper;
        private LinearLayout container1;
        private LinearLayout container2;
        private View firstView;
        private View secondView;
        private Animation pushRightIn;
        private Animation pushRightOut;
        private Animation pushLeftIn;
        private Animation pushLeftOut;

        public TwoViewSlider(ViewFlipper flipper,
                             LinearLayout container1,
                             LinearLayout container2,
                             View view1,
                             View view2) {
            this.flipper = flipper;
            this.container1 = container1;
            this.container2 = container2;
            this.firstView = view1;
            this.secondView = view2;

            pushRightIn = AnimationUtils.loadAnimation(LockScreenActivity.this, R.anim.push_right_in);
            pushRightOut = AnimationUtils.loadAnimation(LockScreenActivity.this, R.anim.push_right_out);
            pushLeftIn = AnimationUtils.loadAnimation(LockScreenActivity.this, R.anim.push_left_in);
            pushLeftOut = AnimationUtils.loadAnimation(LockScreenActivity.this, R.anim.push_left_out);
        }

        public void showNewPasswordField() {
            if (firstIsShown) {
                return;
            }
            flipper.setInAnimation(pushRightIn);
            flipper.setOutAnimation(pushRightOut);
            flip();
        }

        public void showConfirmationField() {
            if (!firstIsShown) {
                return;
            }
            flipper.setInAnimation(pushLeftIn);
            flipper.setOutAnimation(pushLeftOut);
            flip();
        }

        private void flip() {
            if (firstIsShown) {
                firstIsShown = false;
                container2.removeAllViews();
                container2.addView(secondView);
            }
            else {
                firstIsShown = true;
                container1.removeAllViews();
                container1.addView(firstView);
            }
            flipper.showNext();
        }
    }
}