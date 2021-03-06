package com.twigafoods.daraja;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.twigafoods.daraja.model.AccessToken;
import com.twigafoods.daraja.model.LNMExpress;
import com.twigafoods.daraja.model.LNMResult;
import com.twigafoods.daraja.network.ApiClient;
import com.twigafoods.daraja.network.URLs;
import com.twigafoods.daraja.transaction.TransactionType;
import com.twigafoods.daraja.util.Env;
import com.twigafoods.daraja.util.Settings;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class Daraja {
    private String BASE_URL;
    private String CONSUMER_KEY;
    private String CONSUMER_SECRET;

    @Nullable
    private AccessToken accessToken;

    private Daraja(Env env, String CONSUMER_KEY, String CONSUMER_SECRET) {
        this.CONSUMER_KEY = CONSUMER_KEY;
        this.CONSUMER_SECRET = CONSUMER_SECRET;
        this.BASE_URL = (env == Env.SANDBOX) ? URLs.SANDBOX_BASE_URL : URLs.PRODUCTION_BASE_URL;
    }

    //TODO :: CHECK FOR INTERNET CONNECTION
    //Generate the Auth Token
    public static Daraja with(String consumerKey, String consumerSecret, DarajaListener<AccessToken> darajaListener) {
        return with(consumerKey, consumerSecret, Env.SANDBOX, darajaListener);
    }

    public static Daraja with(String CONSUMER_KEY, String CONSUMER_SECRET, Env env, DarajaListener<AccessToken> listener) {
        Daraja daraja = new Daraja(env, CONSUMER_KEY, CONSUMER_SECRET);
        daraja.auth(listener);
        return daraja;
    }

    private void auth(final DarajaListener<AccessToken> listener) {
        //Use Sandbox Base URL
        ApiClient.getAuthAPI(CONSUMER_KEY, CONSUMER_SECRET, BASE_URL).getAccessToken().enqueue(new Callback<AccessToken>() {
            @Override
            public void onResponse(@NonNull Call<AccessToken> call, @NonNull Response<AccessToken> response) {
                if (response.isSuccessful()) {
                    AccessToken accessToken = response.body();
                    if (accessToken != null) {
                        Daraja.this.accessToken = accessToken;
                        listener.onResult(accessToken);
                        return;
                    }
                }
                listener.onError("Authentication Failed");
            }

            @Override
            public void onFailure(@NonNull Call<AccessToken> call, @NonNull Throwable t) {
                listener.onError("Authentication Failed: " + t.getLocalizedMessage());
            }
        });
    }

    //Request the STK Push
    public void sendSTKPush(LNMExpress lnmExpress, final DarajaListener<LNMResult> listener) {

        if (accessToken == null) {
            listener.onError("Not Authenticated");
            return;
        }

        String sanitizedPhoneNumber = Settings.formatPhoneNumber(lnmExpress.getPhoneNumber());
        String sanitizedPartyA = Settings.formatPhoneNumber(lnmExpress.getPartyA());
        String timeStamp = Settings.generateTimestamp();
        String generatedPassword = Settings.generatePassword(lnmExpress.getBusinessShortCode(), lnmExpress.getPassKey(), timeStamp);

        LNMExpress express = new LNMExpress(
                lnmExpress.getBusinessShortCode(),
                generatedPassword,
                timeStamp,
                TransactionType.TRANSACTION_TYPE_CUSTOMER_PAYBILL_ONLINE,
                lnmExpress.getAmount(),
                sanitizedPartyA,
                lnmExpress.getPartyB(),
                sanitizedPhoneNumber,
                lnmExpress.getCallBackURL(),
                lnmExpress.getAccountReference(),
                lnmExpress.getTransactionDesc()
        );

        //Use Sandbox Base URL
        ApiClient.getAPI(BASE_URL, accessToken.getAccess_token()).getLNMPesa(express).enqueue(new Callback<LNMResult>() {
            @Override
            public void onResponse(@NonNull Call<LNMResult> call, @NonNull Response<LNMResult> response) {
                if (response.isSuccessful()) {
                    LNMResult lnmResult = response.body();
                    if (lnmResult != null) {
                        listener.onResult(lnmResult);
                        return;
                    }
                }
                listener.onError("Lipa na M-Pesa Failed");
            }

            @Override
            public void onFailure(@NonNull Call<LNMResult> call, @NonNull Throwable t) {
                listener.onError("Lipa na M-Pesa Failed: " + t.getLocalizedMessage());
            }
        });
    }
}
