<?php

require_once 'shared.php';

function calculateOrderAmount($items) {
	// Replace this constant with a calculation of the order's amount
	// Calculate the order total on the server to prevent
	// people from directly manipulating the amount on the client
	return 1400;
}

try {
  $intent = \Stripe\PaymentIntent::create([
    'amount' => calculateOrderAmount($body->items),
    'currency' => $body->currency,
    'payment_method' => $body->paymentMethodId,
    'error_on_requires_action' => true,
    'confirm' => true,
  ]);
  // The payment is complete and the money has been moved
  // You can add any post-payment code here (e.g. shipping, fulfillment, etc)

  // Send the client secret to the client to use in the demo
  echo json_encode(['clientSecret' => $intent->client_secret]);
} catch (\Stripe\Error\Card $e) {
  if ($e->getCode() == 'authentication_required') {
    echo json_encode([
      'error' => 'This card requires authentication in order to proceeded. Please use a different card'
    ]);  
  } else {
    echo json_encode([
      'error' => $e->getMessage()
    ]);
  }
}

