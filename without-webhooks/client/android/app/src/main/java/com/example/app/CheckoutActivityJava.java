package com.example.app;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.stripe.android.ApiResultCallback;
import com.stripe.android.PaymentConfiguration;
import com.stripe.android.PaymentIntentResult;
import com.stripe.android.Stripe;
import com.stripe.android.model.PaymentIntent;
import com.stripe.android.model.PaymentMethod;
import com.stripe.android.model.PaymentMethodCreateParams;
import com.stripe.android.view.CardInputWidget;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class CheckoutActivityJava extends AppCompatActivity {
    /**
     * This example collects card payments, implementing the guide here: https://stripe.com/docs/payments/accept-a-payment-synchronously#android
     *
     * To run this app, follow the steps here: https://github.com/stripe-samples/accept-a-card-payment#how-to-run-locally
     */
    // 10.0.2.2 is the Android emulator's alias to localhost
    private static final String BACKEND_URL = "http://10.0.2.2:4242/";

    private OkHttpClient httpClient = new OkHttpClient();
    private Stripe stripe;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_checkout);
        loadPage();
    }

    private void loadPage() {
        // Clear the card widget
        CardInputWidget cardInputWidget = findViewById(R.id.cardInputWidget);
        cardInputWidget.clear();

        // For added security, our sample app gets the publishable key from the server
        Request request = new Request.Builder()
                .url(BACKEND_URL + "stripe-key")
                .get()
                .build();
        httpClient.newCall(request)
                .enqueue(new StripeKeyCallback(this));
    }

    private void pay() {
        CardInputWidget cardInputWidget = findViewById(R.id.cardInputWidget);
        PaymentMethodCreateParams params = cardInputWidget.getPaymentMethodCreateParams();

        if (params == null) {
            return;
        }
        stripe.createPaymentMethod(params, new ApiResultCallback<PaymentMethod>() {
            @Override
            public void onSuccess(@NonNull PaymentMethod result) {
                // Create and confirm the PaymentIntent by calling the sample server's /pay endpoint.
                pay(result.id, null);
            }

            @Override
            public void onError(@NonNull Exception e) {

            }
        });
    }

    private void pay(@Nullable String paymentMethodId, @Nullable String paymentIntentId) {
        final MediaType mediaType = MediaType.get("application/json; charset=utf-8");
        final String json;
        if (paymentMethodId != null) {
            json = "{"
                    + "\"useStripeSdk\":true,"
                    + "\"paymentMethodId\":" + "\"" + paymentMethodId + "\","
                    + "\"currency\":\"usd\","
                    + "\"items\":["
                    + "{\"id\":\"photo_subscription\"}"
                    + "]"
                    + "}";
        } else {
            json = "{"
                    + "\"paymentIntentId\":" +  "\"" + paymentIntentId + "\""
                    + "}";
        }
        RequestBody body = RequestBody.create(json, mediaType);
        Request request = new Request.Builder()
                .url(BACKEND_URL + "pay")
                .post(body)
                .build();
        httpClient
                .newCall(request)
                .enqueue(new PayCallback(this, stripe));
    }

    private void displayAlert(@NonNull String title, @NonNull String message, boolean restartDemo) {
        runOnUiThread(() -> {
            final AlertDialog.Builder builder =
                    new AlertDialog.Builder(this)
                            .setTitle(title)
                            .setMessage(message);
            new GsonBuilder()
                    .setPrettyPrinting()
                    .create();
            if (restartDemo) {
                builder.setPositiveButton("Restart demo",
                        (DialogInterface dialog, int index) -> loadPage());
            } else {
                builder.setPositiveButton("Ok", null);
            }
            builder
                    .create()
                    .show();
        });
    }

    private void onRetrievedKey(@NonNull String stripePublishableKey) {
        // Configure the SDK with your Stripe publishable key so that it can make requests to the Stripe API
        final Context applicationContext = getApplicationContext();
        PaymentConfiguration.init(applicationContext, stripePublishableKey);
        stripe = new Stripe(applicationContext, stripePublishableKey);

        // Hook up the pay button to the card widget and stripe instance
        Button payButton = findViewById(R.id.payButton);
        payButton.setOnClickListener((View view) -> pay());
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Handle the result of stripe.confirmPayment
        stripe.onPaymentResult(requestCode, data, new PaymentResultCallback(this));
    }

    private static final class StripeKeyCallback implements Callback {
        @NonNull private final WeakReference<CheckoutActivityJava> activityRef;

        private StripeKeyCallback(@NonNull CheckoutActivityJava activity) {
            this.activityRef = new WeakReference<>(activity);
        }

        @Override
        public void onFailure(@NonNull Call call, @NonNull IOException e) {
            final CheckoutActivityJava activity = activityRef.get();
            if (activity == null) {
                return;
            }

            activity.runOnUiThread(() ->
                    Toast.makeText(activity, "Error: " + e.toString(), Toast.LENGTH_LONG)
                            .show()
            );
        }

        @Override
        public void onResponse(@NonNull Call call, @NonNull final Response response)
                throws IOException {
            final CheckoutActivityJava activity = activityRef.get();
            if (activity == null) {
                return;
            }

            if (!response.isSuccessful()) {
                activity.runOnUiThread(() -> Toast.makeText(activity,
                        "Error: " + response.toString(), Toast.LENGTH_LONG).show());
            } else {
                Gson gson = new Gson();
                Type type = new TypeToken<Map<String, String>>(){}.getType();
                final ResponseBody responseBody = response.body();
                final Map<String, String> responseMap;
                if (responseBody != null) {
                    responseMap = gson.fromJson(responseBody.string(), type);
                } else {
                    responseMap = new HashMap<>();
                }

                final String stripePublishableKey = responseMap.get("publishableKey");
                if (stripePublishableKey != null) {
                    activity.runOnUiThread(() ->
                            activity.onRetrievedKey(stripePublishableKey));
                }
            }
        }
    }

    private static final class PayCallback implements Callback {
        @NonNull private final WeakReference<CheckoutActivityJava> activityRef;
        @NonNull private final Stripe stripe;

        private PayCallback(@NonNull CheckoutActivityJava activity, @NonNull Stripe stripe) {
            this.activityRef = new WeakReference<>(activity);
            this.stripe = stripe;
        }

        @Override
        public void onFailure(@NonNull Call call, @NonNull IOException e) {
            final CheckoutActivityJava activity = activityRef.get();
            if (activity == null) {
                return;
            }

            activity.runOnUiThread(() ->
                    Toast.makeText(activity, "Error: " + e.toString(), Toast.LENGTH_LONG)
                            .show()
            );
        }

        @Override
        public void onResponse(@NonNull Call call, @NonNull final Response response)
                throws IOException {
            final CheckoutActivityJava activity = activityRef.get();
            if (activity == null) {
                return;
            }

            if (!response.isSuccessful()) {
                activity.runOnUiThread(() ->
                        Toast.makeText(activity, "Error: " + response.toString(), Toast.LENGTH_LONG)
                                .show());
            } else {
                Gson gson = new Gson();
                Type type = new TypeToken<Map<String, String>>(){}.getType();
                final ResponseBody responseBody = response.body();
                final Map<String, String> responseMap;
                if (responseBody != null) {
                    responseMap = gson.fromJson(responseBody.string(), type);
                } else {
                    responseMap = new HashMap<>();
                }

                String error = responseMap.get("error");
                String paymentIntentClientSecret = responseMap.get("clientSecret");
                String requiresAction = responseMap.get("requiresAction");

                if (error != null) {
                    activity.displayAlert("Error", error, false);
                } else if (paymentIntentClientSecret != null) {
                    if ("true".equals(requiresAction)) {
                        activity.runOnUiThread(() ->
                                stripe.handleNextActionForPayment(activity, paymentIntentClientSecret));
                    } else {
                        activity.displayAlert("Payment succeeded",
                                paymentIntentClientSecret, true);
                    }
                }

            }
        }
    }

    private static final class PaymentResultCallback
            implements ApiResultCallback<PaymentIntentResult> {
        private final WeakReference<CheckoutActivityJava> activityRef;

        PaymentResultCallback(@NonNull CheckoutActivityJava activity) {
            activityRef = new WeakReference<>(activity);
        }

        @Override
        public void onSuccess(@NonNull PaymentIntentResult result) {
            final CheckoutActivityJava activity = activityRef.get();
            if (activity == null) {
                return;
            }

            PaymentIntent paymentIntent = result.getIntent();
            PaymentIntent.Status status = paymentIntent.getStatus();
            if (status == PaymentIntent.Status.Succeeded) {
                // Payment completed successfully
                activity.runOnUiThread(() -> {
                    Gson gson = new GsonBuilder().setPrettyPrinting().create();
                    activity.displayAlert("Payment completed",
                            gson.toJson(paymentIntent), true);
                });
            } else if (status == PaymentIntent.Status.RequiresPaymentMethod) {
                // Payment failed – allow retrying using a different payment method
                activity.runOnUiThread(() -> {
                    final PaymentIntent.Error error = paymentIntent.getLastPaymentError();
                    final String errorMessage;
                    if (error != null && error.getMessage() != null) {
                        errorMessage = error.getMessage();
                    } else {
                        errorMessage = "Unknown error";
                    }
                    activity.displayAlert("Payment failed", errorMessage, false);
                });
            } else if (status == PaymentIntent.Status.RequiresConfirmation) {
                // After handling a required action on the client, the status of the PaymentIntent is
                // requires_confirmation. You must send the PaymentIntent ID to your backend
                // and confirm it to finalize the payment. This step enables your integration to
                // synchronously fulfill the order on your backend and return the fulfillment result
                // to your client.
                activity.pay(null, paymentIntent.getId());
            }
        }

        @Override
        public void onError(@NonNull Exception e) {
            final CheckoutActivityJava activity = activityRef.get();
            if (activity == null) {
                return;
            }

            // Payment request failed – allow retrying using the same payment method
            activity.runOnUiThread(() ->
                    activity.displayAlert("Error", e.toString(), false)
            );
        }
    }
}
