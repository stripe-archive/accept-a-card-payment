var orderData = {
  items: [{ id: "photo-subscription" }],
  currency: "usd",
  billing_details: {
    name: 'Jenny Rosen'
  }
};

// Disable the 'Pay' button until Stripe is set up on the page
document.querySelector("button").disabled = true;

fetch("/stripe-key")
  .then(function(result) {
    return result.json();
  })
  .then(function(data) {
    return setupElements(data);
  })
  .then(function({ stripe, card }) {
    document.querySelector("button").disabled = false;

    var form = document.getElementById("payment-form");
    form.addEventListener("submit", function(event) {
      event.preventDefault();
      pay(stripe, card);
    });
  });

/* Set up Stripe Elements to use in checkout form */
var setupElements = function(data) {
  
  // A reference to Stripe.js
  var stripe = Stripe(data.publishableKey);
  
  var elements = stripe.elements();
  var style = {
    base: {
      color: "#32325d",
      fontFamily: '"Helvetica Neue", Helvetica, sans-serif',
      fontSmoothing: "antialiased",
      fontSize: "16px",
      "::placeholder": {
        color: "#aab7c4"
      }
    },
    invalid: {
      color: "#fa755a",
      iconColor: "#fa755a"
    }
  };

  var card = elements.create("card", { style: style });
  card.mount("#card-element");

  return {
    stripe: stripe,
    card: card,
    clientSecret: data.clientSecret
  };
};

/* Collect card details and pay for the order */
var pay = function(stripe, card) {
  changeLoadingState(true);

  // Collects card details and creates a PaymentMethod
  stripe
    .createPaymentMethod("card", card)
    .then(function(result) {
      if (result.error) {
        showError(result.error.message);
      } else {
        orderData.paymentMethodId = result.paymentMethod.id;

        return fetch("/pay", {
          method: "POST",
          headers: {
            "Content-Type": "application/json"
          },
          body: JSON.stringify(orderData)
        });
      }
    })
    .then(function(result) {
      return result.json();
    })
    .then(function(response) {
      if (response.error) {
        showError(response.error);
      } else {
        orderComplete(stripe, card, response.clientSecret);
      }
    });
};

/* ------- Post-payment helpers ------- */

/* Shows a success / error message when the payment is complete */
var orderComplete = function(stripe, card, clientSecret) {
  stripe.retrievePaymentIntent(clientSecret).then(function(result) {
    
  // Submit payment to Stripe:
  // https://stripe.com/docs/payments/accept-a-payment#web-submit-payment
  stripe.confirmCardPayment(clientSecret, {
      payment_method: {
        card: card,
        billing_details: orderData.billing_details
      }
    }).then(function(result) {
      if (result.error) {
        // Show error to your customer (e.g., insufficient funds)
        console.log(result.error.message);
      } else {
        // The payment has been processed!
        if (result.paymentIntent.status === 'succeeded') {
          // Show a success message to your customer
          
          var paymentIntent = result.paymentIntent;
          var paymentIntentJson = JSON.stringify(paymentIntent, null, 2);
          
          document.querySelector(".sr-payment-form").classList.add("hidden");
          document.querySelector("pre").textContent = paymentIntentJson;
          
          document.querySelector(".sr-result").classList.remove("hidden");
          setTimeout(function() {
            document.querySelector(".sr-result").classList.add("expand");
          }, 200);
          
          changeLoadingState(false);
          
          // There's a risk of the customer closing the window before callback
          // execution. Set up a webhook or plugin to listen for the
          // payment_intent.succeeded event that handles any business critical
          // post-payment actions.
        }
      }
    });
  });
};

// Error handling
var showError = function(errorMsgText) {
  changeLoadingState(false);
  var errorMsg = document.querySelector(".sr-field-error");
  errorMsg.textContent = errorMsgText;
  setTimeout(function() {
    errorMsg.textContent = "";
  }, 4000);
};

// Show a spinner on payment submission
var changeLoadingState = function(isLoading) {
  if (isLoading) {
    document.querySelector("button").disabled = true;
    document.querySelector("#spinner").classList.remove("hidden");
    document.querySelector("#button-text").classList.add("hidden");
  } else {
    document.querySelector("button").disabled = false;
    document.querySelector("#spinner").classList.add("hidden");
    document.querySelector("#button-text").classList.remove("hidden");
  }
};
