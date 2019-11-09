<?php

require_once 'shared.php';

function calculateOrderAmount($items) {
	// Replace this constant with a calculation of the order's amount
	// Calculate the order total on the server to prevent
	// people from directly manipulating the amount on the client
	return 1400;
}

function generateResponse($intent) 
{
  switch($intent->status) {
    case "requires_action":
    case "requires_source_action":
      // Card requires authentication
      return [
        'requiresAction'=> true,
        'paymentIntentId'=> $intent->id,
        'clientSecret'=> $intent->client_secret
      ];
    case "requires_payment_method":
    case "requires_source":
      // Card was not properly authenticated, suggest a new payment method
      return [
        error => "Your card was denied, please provide a new payment method"
      ];
    case "succeeded":
      // Payment is complete, authentication not required
      // To cancel the payment after capture you will need to issue a Refund (https://stripe.com/docs/api/refunds)
      return ['clientSecret' => $intent->client_secret];
  }
}

try {
  if($body->paymentMethodId != null) {
    // Create new PaymentIntent with a PaymentMethod ID from the client.
    $intent = \Stripe\PaymentIntent::create([
      "amount" => calculateOrderAmount($body->items),
      "currency" => $body->currency,
      "payment_method" => $body->paymentMethodId,
      "confirmation_method" => "manual",
      "confirm" => true,
      // If a mobile client passes `useStripeSdk`, set `use_stripe_sdk=true`
      // to take advantage of new authentication features in mobile SDKs
      "use_stripe_sdk" => $body->useStripeSdk,

    ]);
    // After create, if the PaymentIntent's status is succeeded, fulfill the order.
    } else if ($body->paymentIntentId != null) {
    // Confirm the PaymentIntent to finalize payment after handling a required action
    // on the client.
    $intent = \Stripe\PaymentIntent::retrieve($body->paymentIntentId);
    $intent->confirm();
    // After confirm, if the PaymentIntent's status is succeeded, fulfill the order.
  }  
  $output = generateResponse($intent);

  echo json_encode($output);
} catch (\Stripe\Error\Card $e) {
  echo json_encode([
    'error' => $e->getMessage()
  ]);
}

