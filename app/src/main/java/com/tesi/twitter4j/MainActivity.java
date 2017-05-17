package com.tesi.twitter4j;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.StrictMode;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.webkit.CookieManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.util.*;

import twitter4j.Friendship;
import twitter4j.IDs;
import twitter4j.PagableResponseList;
import twitter4j.StatusUpdate;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.User;
import twitter4j.auth.AccessToken;
import twitter4j.auth.RequestToken;
import twitter4j.conf.Configuration;
import twitter4j.conf.ConfigurationBuilder;

import static android.R.id.list;

public class MainActivity extends Activity implements View.OnClickListener {
    // con queste chiavi posso usare le API e il sistema di login
    private static final String PREF_NAME = "sample_twitter_pref";
    private static final String PREF_KEY_OAUTH_TOKEN = "oauth_token";
    private static final String PREF_KEY_OAUTH_SECRET = "oauth_token_secret";
    private static final String PREF_KEY_TWITTER_LOGIN = "is_twitter_loggedin";
    private static final String PREF_USER_NAME = "twitter_user_name";

    /* distinguere in modo univoco la richiesta */
    public static final int WEBVIEW_REQUEST_CODE = 100;

    private ProgressDialog pDialog;

    private static Twitter twitter;
    private static RequestToken requestToken;

    private static SharedPreferences mSharedPreferences;

    private EditText mShareEditText;
    private TextView userName;
    private View loginLayout;
    private View shareLayout;
    private View response_layout;
    private View backLayout;

    private TextView myfr;
    private TextView myfol;

    private String consumerKey = null;
    private String consumerSecret = null;
    private String callbackUrl = null;
    private String oAuthVerifier = null;

    void onLogoutClick() {
        logout();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        /* initializing twitter parameters from string.xml */
        initTwitterConfigs();

		/* Enabling strict mode */
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        setContentView(R.layout.activity_main);
        loginLayout = (RelativeLayout) findViewById(R.id.login_layout);
        shareLayout = (RelativeLayout) findViewById(R.id.share_layout);
        mShareEditText = (EditText) findViewById(R.id.share_text);
        userName = (TextView) findViewById(R.id.user_name);
        response_layout = (LinearLayout) findViewById(R.id.response_layout);
        backLayout = (RelativeLayout) findViewById(R.id.backToLayout);
        myfr = (TextView) findViewById(R.id.myfrineds);
        myfol = (TextView) findViewById(R.id.myfrineds);


        findViewById(R.id.btn_login).setOnClickListener(this);
        findViewById(R.id.btn_share).setOnClickListener(this);
        findViewById(R.id.btn_getFriendList).setOnClickListener(this);
        findViewById(R.id.btn_logout).setOnClickListener(this);
        findViewById(R.id.btn_getFollowersList).setOnClickListener(this);
        findViewById(R.id.backTo).setOnClickListener(this);

        if (TextUtils.isEmpty(consumerKey) || TextUtils.isEmpty(consumerSecret)) {
            Toast.makeText(this, "Twitter key and secret not configured",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        /* Initialize application preferences */
        mSharedPreferences = getSharedPreferences(PREF_NAME, 0);
        boolean isLoggedIn = mSharedPreferences.getBoolean(PREF_KEY_TWITTER_LOGIN, false);

        /*  if already logged in, then hide login layout and show share layout */
        if (isLoggedIn) {
            loginLayout.setVisibility(View.GONE);
            shareLayout.setVisibility(View.VISIBLE);

            String username = mSharedPreferences.getString(PREF_USER_NAME, "");
            userName.setText(getResources().getString(R.string.hello)
                    + username);

        } else {
            loginLayout.setVisibility(View.VISIBLE);
            shareLayout.setVisibility(View.GONE);

            Uri uri = getIntent().getData();

            if (uri != null && uri.toString().startsWith(callbackUrl)) {

                String verifier = uri.getQueryParameter(oAuthVerifier);

                try {

					/* Getting oAuth authentication token */
                    AccessToken accessToken = twitter.getOAuthAccessToken(requestToken, verifier);

					/* Getting user id form access token */
                    long userID = accessToken.getUserId();
                    final User user = twitter.showUser(userID);
                    final String username = user.getName();

					/* save updated token */
                    saveTwitterInfo(accessToken);

                    loginLayout.setVisibility(View.GONE);
                    shareLayout.setVisibility(View.VISIBLE);
                    userName.setText(getString(R.string.hello) + username);

                } catch (Exception e) {
                    Log.e("Failed to login Twitter", e.getMessage());
                }
            }

        }

    }

    /**
     * Le info dell utente loggato vengono salvate alla prima autenticaz.
     * Quindi finchè l utente ha un access token valido
     * non dovrà dare ancora il login - non gli viene mostrato l'xml per loggarsi
     */
    private void saveTwitterInfo(AccessToken accessToken) {

        long userID = accessToken.getUserId();

        User user;
        try {
            user = twitter.showUser(userID);

            String username = user.getName();

			/* Storing oAuth tokens to shared preferences */
            SharedPreferences.Editor e = mSharedPreferences.edit();
            e.putString(PREF_KEY_OAUTH_TOKEN, accessToken.getToken());
            e.putString(PREF_KEY_OAUTH_SECRET, accessToken.getTokenSecret());
            e.putBoolean(PREF_KEY_TWITTER_LOGIN, true);
            e.putString(PREF_USER_NAME, username);
            e.commit();

        } catch (TwitterException e1) {
            e1.printStackTrace();
        }
    }

    /* Reading twitter essential configuration parameters from strings.xml */
    private void initTwitterConfigs() {
        consumerKey = getString(R.string.twitter_consumer_key);
        consumerSecret = getString(R.string.twitter_consumer_secret);
        callbackUrl = getString(R.string.twitter_callback);
        oAuthVerifier = getString(R.string.twitter_oauth_verifier);
    }

    private void loginToTwitter() {
        boolean isLoggedIn = mSharedPreferences.getBoolean(PREF_KEY_TWITTER_LOGIN, false);

        if (!isLoggedIn) {
            final ConfigurationBuilder builder = new ConfigurationBuilder();
            builder.setOAuthConsumerKey(consumerKey);
            builder.setOAuthConsumerSecret(consumerSecret);

            final Configuration configuration = builder.build();
            final TwitterFactory factory = new TwitterFactory(configuration);
            twitter = factory.getInstance();

            try {
                requestToken = twitter.getOAuthRequestToken(callbackUrl);

                /**
                 *  Loading twitter login page on webview for authorization
                 *  Once authorized, results are received at onActivityResult
                 *  */
                final Intent intent = new Intent(this, WebViewActivity.class);
                intent.putExtra(WebViewActivity.EXTRA_URL, requestToken.getAuthenticationURL());
                startActivityForResult(intent, WEBVIEW_REQUEST_CODE);

            } catch (TwitterException e) {
                e.printStackTrace();
            }
        } else {

            loginLayout.setVisibility(View.GONE);
            shareLayout.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        if (resultCode == Activity.RESULT_OK) {
            String verifier = data.getExtras().getString(oAuthVerifier);
            try {
                AccessToken accessToken = twitter.getOAuthAccessToken(requestToken, verifier);

                long userID = accessToken.getUserId();
                final User user = twitter.showUser(userID);
                String username = user.getName();

                saveTwitterInfo(accessToken);

                loginLayout.setVisibility(View.GONE);
                shareLayout.setVisibility(View.VISIBLE);
                userName.setText(MainActivity.this.getResources().getString(
                        R.string.hello) + username);

            } catch (Exception e) {
                Log.e("Twitter Login Failed", e.getMessage());
            }
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_login:
                loginToTwitter();
                break;
            case R.id.btn_share:
                final String status = mShareEditText.getText().toString();

                if (status.trim().length() > 0) {
                    new updateTwitterStatus().execute(status);
                } else {
                    Toast.makeText(this, "Message is empty!!", Toast.LENGTH_SHORT).show();
                }
                break;
            case R.id.btn_getFriendList:
                if (getFriendList().size() > 0) {

                    response_layout.setVisibility(View.VISIBLE);
                    backLayout.setVisibility(View.VISIBLE);
                    loginLayout.setVisibility(View.GONE);
                    shareLayout.setVisibility(View.GONE);

                    for(int i = 0; i < getFriendList().size(); i ++){
                        User friend = getFriendList().get(i);
                        String str_fr = friend.getName();
                        myfr.append("-> "+str_fr+"\n");
                        //Toast.makeText(getBaseContext(), friend.getName(), Toast.LENGTH_SHORT).show();

                    }
                   // Toast.makeText(getBaseContext(), "Trovati friends", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Forever alone!!", Toast.LENGTH_SHORT).show();
                }
                break;
            case R.id.btn_logout:
                logout();
                break;
            case R.id.btn_getFollowersList:

                if (getFollowersList().size() > 0) {

                    response_layout.setVisibility(View.VISIBLE);
                    backLayout.setVisibility(View.VISIBLE);
                    loginLayout.setVisibility(View.GONE);
                    shareLayout.setVisibility(View.GONE);

                    // Toast.makeText(getBaseContext(), "Trovati followers", Toast.LENGTH_SHORT).show();
                    for(int i = 0; i < getFollowersList().size(); i++){
                        User follower = getFollowersList().get(i);
                        String str_fol = follower.getName();
                        myfol.append("->  " + str_fol+"\n");
                        //Toast.makeText(getBaseContext(), follower.getName(), Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(this, "No Followers! ...alone", Toast.LENGTH_SHORT).show();
                }
                break;
            case R.id.backTo:
                response_layout.setVisibility(View.GONE);
                backLayout.setVisibility(View.GONE);
                loginLayout.setVisibility(View.GONE);
                shareLayout.setVisibility(View.VISIBLE);
        }
    }

    //list dei followers e friends
    public List<User> getFriendList() {
        List<User> listFriends = new ArrayList<User>();
        try {
            // get friends
            long cursor = -1;
            PagableResponseList<User> pagableFollowings;
            do {
                pagableFollowings = twitter.getFriendsList(twitter.getId(), cursor);
                for (User user : pagableFollowings) {
                    listFriends.add(user); // ArrayList<User>
                }
            } while ((cursor = pagableFollowings.getNextCursor()) != 0);

            /*
             followers

            cursor = -1;
            PagableResponseList<User> pagableFollowers;
            do {
                pagableFollowers = twitter.getFollowersList(twitter.getId(), cursor);
                for (User user : pagableFollowers) {
                    listFollowers.add(user); // ArrayList<User>
                }
            } while ((cursor = pagableFollowers.getNextCursor()) != 0);
            */

        } catch (TwitterException e) {
            e.printStackTrace();
        }
        return listFriends;
    }

    public List<User> getFollowersList() {
        long cursor = -1;
        PagableResponseList<User> pagableFollowers;
        List<User> listFollowers = new ArrayList<>();
        do {
            try {
                pagableFollowers = twitter.getFollowersList(twitter.getId(), cursor);

                for (User user : pagableFollowers) {
                    listFollowers.add(user); //
                }
            } catch (TwitterException e) {
                e.printStackTrace();
            }
            return listFollowers;
        }
        while ((cursor = pagableFollowers.getNextCursor()) != 0);
    }

    /*
    Ricorda... è solo un fake logout - mi resetto solo la view
    Twitter4J is authenticated by the API in stateless fashion
    using Basic auth header. It's totally stateless and logging
    out is not required
     */
    private void logout() {

        SharedPreferences.Editor e = mSharedPreferences.edit();
        e.remove(PREF_KEY_OAUTH_TOKEN);
        e.remove(PREF_KEY_OAUTH_SECRET);
        e.remove(PREF_KEY_TWITTER_LOGIN);
        e.apply();

        CookieManager.getInstance().setCookie(".twitter.com", "auth_token=''");

        Log.i("Activity", "logout");
        loginLayout.setVisibility(View.VISIBLE);
        shareLayout.setVisibility(View.GONE);
    }

    class updateTwitterStatus extends AsyncTask<String, String, Void> {
        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            pDialog = new ProgressDialog(MainActivity.this);
            pDialog.setMessage("Sto twittando...");
            pDialog.setIndeterminate(false);
            pDialog.setCancelable(false);
            pDialog.show();
        }

        protected Void doInBackground(String... args) {

            String status = args[0];
            try {
                ConfigurationBuilder builder = new ConfigurationBuilder();
                builder.setOAuthConsumerKey(consumerKey);
                builder.setOAuthConsumerSecret(consumerSecret);

                // Access Token
                String access_token = mSharedPreferences.getString(PREF_KEY_OAUTH_TOKEN, "");
                // Access Token Secret
                String access_token_secret = mSharedPreferences.getString(PREF_KEY_OAUTH_SECRET, "");

                AccessToken accessToken = new AccessToken(access_token, access_token_secret);
                Twitter twitter = new TwitterFactory(builder.build()).getInstance(accessToken);

                // Update status
                StatusUpdate statusUpdate = new StatusUpdate(status);
                InputStream is = getResources().openRawResource(+R.drawable.lakesideview);
                statusUpdate.setMedia("test.jpg", is);

                twitter4j.Status response = twitter.updateStatus(statusUpdate);

                Log.d("Status", response.getText());

            } catch (TwitterException e) {
                Log.d("Failed to post!", e.getMessage());
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {

			/* Dismiss the progress dialog after sharing */
            pDialog.dismiss();

            Toast.makeText(MainActivity.this, "Posted to Twitter!", Toast.LENGTH_SHORT).show();

            // Clearing EditText field
            mShareEditText.setText("");
        }

    }
}
