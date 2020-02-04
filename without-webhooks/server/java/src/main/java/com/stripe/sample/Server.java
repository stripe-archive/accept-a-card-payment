package com.stripe.sample;

import java.nio.file.Paths;

import static spark.Spark.get;
import static spark.Spark.post;
import static spark.Spark.staticFiles;
import static spark.Spark.port;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import com.stripe.Stripe;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;

import io.github.cdimascio.dotenv.Dotenv;

public class Server {
    private static Gson gson = new Gson();

    static class StripeKeyResponse {
        private String publishableKey;

        public StripeKeyResponse(String publishableKey) {
            this.publishableKey = publishableKey;
        }
    }

    static class ConfirmPaymentRequest {
        @SerializedName("items")
        Object[] items;
        @SerializedName("paymentIntentId")
        String paymentIntentId;
        @SerializedName("paymentMethodId")
        String paymentMethodId;
        @SerializedName("currency")
        String currency;
        @SerializedName("useStripeSdk")
        String useStripeSdk;

        public Object[] getItems() {
            return items;
        }

        public String getPaymentIntentId() {
            return paymentIntentId;
        }

        public String getPaymentMethodId() {
            return paymentMethodId;
        }

        public String getCurrency() {
            return currency;
        }

        public Boolean getUseStripeSdk() {
            return Boolean.parseBoolean(useStripeSdk);        
        }
    }

    static class PayResponseBody {
        private String clientSecret;
        private Boolean requiresAction;
        private String error;

        public PayResponseBody() {

        }

        public void setClientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
        }

        public void setRequiresAction(Boolean requiresAction) {
            this.requiresAction = requiresAction;
        }

        public void setError(String error) {
            this.error = error;
        }
    }

    static int calculateOrderAmount(Object[] items) {
        // Replace this constant with a calculation of the order's amount
        // Calculate the order total on the server to prevent
        // users from directly manipulating the amount on the client
        return 1400;
    }

    static PayResponseBody generateResponse(PaymentIntent intent, PayResponseBody response) {
        switch (intent.getStatus()) {
        case "requires_action":
        case "requires_source_action":
            // Card requires authentication
            response.setClientSecret(intent.getClientSecret());
            response.setRequiresAction(true);
            break;
        case "requires_payment_method":
        case "requires_source":
            // Card was not properly authenticated, suggest a new payment method
            response.setError("Your card was denied, please provide a new payment method");
            break;
        case "succeeded":
            System.out.println("💰 Payment received!");
            // Payment is complete, authentication not required
            // To cancel the payment you will need to issue a Refund
            // (https://stripe.com/docs/api/refunds)
            response.setClientSecret(intent.getClientSecret());
            break;
        default:
            response.setError("Unrecognized status");
        }
        return response;
    }

    public static void main(String[] args) {
        port(4242);
        Dotenv dotenv = Dotenv.load();
        Stripe.apiKey = dotenv.get("STRIPE_SECRET_KEY");

        staticFiles.externalLocation(
                Paths.get(Paths.get("").toAbsolutePath().toString(), dotenv.get("STATIC_DIR")).normalize().toString());

        get("/stripe-key", (request, response) -> {
            response.type("application/json");
            // Send publishable key to client
            return gson.toJson(new StripeKeyResponse(dotenv.get("STRIPE_PUBLISHABLE_KEY")));
        });

        post("/pay", (request, response) -> {
            ConfirmPaymentRequest confirmRequest = gson.fromJson(request.body(), ConfirmPaymentRequest.class);

            PaymentIntent intent = null;
            PayResponseBody responseBody = new PayResponseBody();
            try {
                if (confirmRequest.getPaymentMethodId() != null) {
                    int orderAmount = calculateOrderAmount(confirmRequest.getItems());
                    // Create new PaymentIntent with a PaymentMethod ID from the client.
                    PaymentIntentCreateParams.Builder createParamsBuilder = new PaymentIntentCreateParams.Builder()
                            .setCurrency(confirmRequest.getCurrency()).setAmount(new Long(orderAmount))
                            .setPaymentMethod(confirmRequest.getPaymentMethodId())
                            .setConfirmationMethod(PaymentIntentCreateParams.ConfirmationMethod.MANUAL).setConfirm(true);
                    if (confirmRequest.getUseStripeSdk()) {
                        // If a mobile client passes `useStripeSdk`, set `use_stripe_sdk=true`
                        // to take advantage of new authentication features in mobile SDKs
                        createParamsBuilder.setUseStripeSdk(confirmRequest.getUseStripeSdk());
                    }
                    PaymentIntentCreateParams createParams = createParamsBuilder.build();
                    intent = PaymentIntent.create(createParams);
                    // After create, if the PaymentIntent's status is succeeded, fulfill the order.
                } else if (confirmRequest.getPaymentIntentId() != null) {
                    // Confirm the PaymentIntent to finalize payment after handling a required
                    // action on the client.
                    intent = PaymentIntent.retrieve(confirmRequest.getPaymentIntentId());
                    intent = intent.confirm();
                    // After confirm, if the PaymentIntent's status is succeeded, fulfill the order.
                }

                responseBody = generateResponse(intent, responseBody);
            } catch (Exception e) {
                // Handle "hard declines" e.g. insufficient funds, expired card, etc
                // See https://stripe.com/docs/declines/codes for more
                responseBody.setError(e.getMessage());
            }

            return gson.toJson(responseBody);
        });
    }
}