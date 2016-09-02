/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016 Jacob Lubecki
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.jlubecki.soundcloud;

import android.content.Intent;
import android.os.Bundle;
import android.support.customtabs.CustomTabsIntent;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckedTextView;

import com.jlubecki.soundcloud.webapi.android.auth.AuthenticationCallback;
import com.jlubecki.soundcloud.webapi.android.auth.AuthenticationStrategy;
import com.jlubecki.soundcloud.webapi.android.auth.SoundCloudAuthenticator;
import com.jlubecki.soundcloud.webapi.android.auth.browser.BrowserSoundCloudAuthenticator;
import com.jlubecki.soundcloud.webapi.android.auth.chrometabs.AuthTabServiceConnection;
import com.jlubecki.soundcloud.webapi.android.auth.chrometabs.ChromeTabsSoundCloudAuthenticator;
import com.jlubecki.soundcloud.webapi.android.auth.models.AuthenticationResponse;
import com.jlubecki.soundcloud.webapi.android.auth.webview.WebViewSoundCloudAuthenticator;

import java.util.ArrayList;

import static com.jlubecki.soundcloud.Constants.AUTH_TOKEN_KEY;
import static com.jlubecki.soundcloud.Constants.CLIENT_ID;
import static com.jlubecki.soundcloud.Constants.CLIENT_SECRET;
import static com.jlubecki.soundcloud.Constants.PREFS_NAME;
import static com.jlubecki.soundcloud.Constants.REDIRECT;

public class MainActivity extends AppCompatActivity {

    // Logging
    private static final String TAG = "MainActivity";

    // Constants
    private static final int REQUEST_CODE_AUTHENTICATE = 1337;

    // Variables
    private BrowserSoundCloudAuthenticator browserAuthenticator;
    private ChromeTabsSoundCloudAuthenticator tabsAuthenticator;
    private WebViewSoundCloudAuthenticator webViewAuthenticator;

    private AuthenticationStrategy strategy;
    private ArrayList<SoundCloudAuthenticator> activeAuthenticators = new ArrayList<>();

    // Views
    private CheckedTextView chromeTabAuthCheckedText;
    private CheckedTextView browserCheckedText;
    private CheckedTextView webViewAuthCheckedText;
    private Button launchAuthButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_layout);

        // Prepare views
        browserCheckedText = (CheckedTextView) findViewById(R.id.ctv_browser_auth);
        chromeTabAuthCheckedText = (CheckedTextView) findViewById(R.id.ctv_chrome_auth);
        webViewAuthCheckedText = (CheckedTextView) findViewById(R.id.ctv_wv_auth);
        launchAuthButton = (Button) findViewById(R.id.btn_begin_auth);

        // Prepare auth methods

        AuthTabServiceConnection serviceConnection = new AuthTabServiceConnection(
                new AuthenticationCallback() {
                    @Override
                    public void onReadyToAuthenticate() {
                        int toolbarColor = ContextCompat.getColor(MainActivity.this, R.color.colorPrimary);
                        int secondaryToolbarColor = ContextCompat.getColor(MainActivity.this, R.color.colorAccent);

                        // Customize Chrome Tabs
                        CustomTabsIntent.Builder builder = tabsAuthenticator.newTabsIntentBuilder()
                                .setToolbarColor(toolbarColor)
                                .setSecondaryToolbarColor(secondaryToolbarColor);

                        tabsAuthenticator.setTabsIntentBuilder(builder);

                        if (chromeTabAuthCheckedText != null) {
                            chromeTabAuthCheckedText.setEnabled(true);
                        }
                    }

                    @Override
                    public void onAuthenticationEnded() {
                        Log.i(TAG, "Auth ended.");
                    }
                });

        tabsAuthenticator = new ChromeTabsSoundCloudAuthenticator(CLIENT_ID, REDIRECT, this, serviceConnection);
        browserAuthenticator = new BrowserSoundCloudAuthenticator(CLIENT_ID, REDIRECT, this);
        webViewAuthenticator = new WebViewSoundCloudAuthenticator(CLIENT_ID, REDIRECT, this, REQUEST_CODE_AUTHENTICATE);

        chromeTabAuthCheckedText.setEnabled(tabsAuthenticator.prepareAuthenticationFlow());
        browserCheckedText.setEnabled(browserAuthenticator.prepareAuthenticationFlow());
        webViewAuthCheckedText.setEnabled(webViewAuthenticator.prepareAuthenticationFlow());

        setupClickListeners();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        getTokenFromIntent(intent);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case REQUEST_CODE_AUTHENTICATE:
                handleAuthRequestCode(requestCode, resultCode, data);
                break;

            default:
                Log.i(TAG, "Other activity result: " + requestCode);
                break;
        }
    }

    @Override
    public void onDestroy() {
        if (tabsAuthenticator != null) {
            tabsAuthenticator.unbindService();
        }

        super.onDestroy();
    }

    // region Helper

    void setupClickListeners() {
        browserCheckedText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                browserCheckedText.setChecked(!browserCheckedText.isChecked());

                if (browserCheckedText.isChecked()) {
                    activeAuthenticators.add(browserAuthenticator);
                } else {
                    activeAuthenticators.remove(browserAuthenticator);
                }
            }
        });

        chromeTabAuthCheckedText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                chromeTabAuthCheckedText.setChecked(!chromeTabAuthCheckedText.isChecked());

                if (chromeTabAuthCheckedText.isChecked()) {
                    activeAuthenticators.add(tabsAuthenticator);
                } else {
                    activeAuthenticators.remove(tabsAuthenticator);
                }
            }
        });

        webViewAuthCheckedText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                webViewAuthCheckedText.setChecked(!webViewAuthCheckedText.isChecked());
                if (webViewAuthCheckedText.isChecked()) {
                    activeAuthenticators.add(webViewAuthenticator);
                } else {
                    activeAuthenticators.remove(webViewAuthenticator);
                }
            }
        });

        launchAuthButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                AuthenticationStrategy.Builder builder = new AuthenticationStrategy.Builder(MainActivity.this);

                for (SoundCloudAuthenticator authenticator : activeAuthenticators) {
                    builder.addAuthenticator(authenticator);
                }

                strategy = builder
                        .setCheckNetwork(true)
                        .onFailure(new AuthenticationStrategy.OnNetworkFailureListener() {
                            @Override
                            public void onFailure(Throwable throwable) {
                                Log.e(TAG, throwable.getMessage());
                            }
                        })
                        .build();

                strategy.authenticate();
            }
        });
    }

    void getTokenFromIntent(Intent intent) {
        String intentInfo = intent != null ? intent.getDataString() : "Null";
        Log.i(TAG, "Trying to get token from intent data: " + intentInfo);

        if (strategy.canAuthenticate(intent)) {
            strategy.getToken(intent, CLIENT_SECRET, new AuthenticationStrategy.ResponseCallback() {
                @Override
                public void onAuthenticationResponse(AuthenticationResponse response) {
                    switch (response.getType()) {
                        case TOKEN:
                            saveToken(response.access_token);
                            openPlayer();
                            break;

                        default:
                            Log.e(TAG, response.toString());
                            break;
                    }
                }

                @Override
                public void onAuthenticationFailed(Throwable throwable) {
                    Log.e(TAG, throwable.getMessage());
                }
            });
        } else {
            Log.w(TAG, "Token could not be obtained from intent.");
        }
    }

    private void handleAuthRequestCode(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQUEST_CODE_AUTHENTICATE) {
            String error = "Code: " + requestCode + " should be: " + REQUEST_CODE_AUTHENTICATE;
            Log.e(TAG, error);

            return;
        }

        if (resultCode == RESULT_OK) {
            getTokenFromIntent(data);
        } else if (resultCode == RESULT_CANCELED) {
            Log.w(TAG, "Authentication was canceled.");
        } else {
            Log.w(TAG, "Unhandled result code: " + resultCode);
        }
    }

    private void saveToken(String token) {
        Log.i(TAG, "Token saved -  " + token);

        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putString(AUTH_TOKEN_KEY, token)
                .apply();
    }

    private void openPlayer() {
        Intent intent = new Intent(MainActivity.this, PlayerActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    // endregion
}
