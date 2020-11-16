import env from "dotenv";
import path from "path";

import express from "express";
import Stripe from "stripe";

// Replace if using a different env file or config.
env.config({ path: "./.env" });

const stripe = new Stripe(process.env.STRIPE_SECRET_KEY, {
  apiVersion: "2020-08-27",
  typescript: true
});

const app = express();
const resolve = path.resolve;

app.use(express.static(process.env.STATIC_DIR));
app.use(express.json());

app.get("/", (_: express.Request, res: express.Response): void => {
  // Serve checkout page.
  const indexPath = resolve(process.env.STATIC_DIR + "/index.html");
  res.sendFile(indexPath);
});

app.get("/stripe-key", (_: express.Request, res: express.Response): void => {
  res.send({ publishableKey: process.env.STRIPE_PUBLISHABLE_KEY });
});

// tslint:disable-next-line: interface-name
interface Order {
  items: object[];
}

const calculateOrderAmount = (order: Order): number => {
  // Replace this constant with a calculation of the order's amount
  // You should always calculate the order total on the server to prevent
  // people from directly manipulating the amount on the client.
  return 1400;
};

app.post(
  "/pay",
  async (req: express.Request, res: express.Response): Promise<void> => {
    const {
      paymentMethodId,
      paymentIntentId,
      items,
      currency,
      useStripeSdk
    }: {
      paymentMethodId: string;
      paymentIntentId: string;
      items: Order;
      currency: string;
      useStripeSdk: boolean;
    } = req.body;

    const orderAmount: number = calculateOrderAmount(items);

    try {
      let intent: Stripe.PaymentIntent;
      if (paymentMethodId) {
        // Create new PaymentIntent with a PaymentMethod ID from the client.
        const params: Stripe.PaymentIntentCreateParams = {
          amount: orderAmount,
          confirm: true,
          confirmation_method: "manual",
          currency,
          payment_method: paymentMethodId,
          // If a mobile client passes `useStripeSdk`, set `use_stripe_sdk=true`
          // to take advantage of new authentication features in mobile SDKs.
          use_stripe_sdk: useStripeSdk
        };
        intent = await stripe.paymentIntents.create(params);
        // After create, if the PaymentIntent's status is succeeded, fulfill the order.
      } else if (paymentIntentId) {
        // Confirm the PaymentIntent to finalize payment after handling a required action
        // on the client.
        intent = await stripe.paymentIntents.confirm(paymentIntentId);
        // After confirm, if the PaymentIntent's status is succeeded, fulfill the order.
      }
      res.send(generateResponse(intent));
    } catch (e) {
      // Handle "hard declines" e.g. insufficient funds, expired card, etc
      // See https://stripe.com/docs/declines/codes for more.
      res.send({ error: e.message });
    }
  }
);

const generateResponse = (
  intent: Stripe.PaymentIntent
):
  | { clientSecret: string; requiresAction: boolean }
  | { clientSecret: string }
  | { error: string } => {
  // Generate a response based on the intent's status
  switch (intent.status) {
    case "requires_action":
      // Card requires authentication
      return {
        clientSecret: intent.client_secret,
        requiresAction: true
      };
    case "requires_payment_method":
      // Card was not properly authenticated, suggest a new payment method
      return {
        error: "Your card was denied, please provide a new payment method"
      };
    case "succeeded":
      // Payment is complete, authentication not required
      // To cancel the payment after capture you will need to issue a Refund (https://stripe.com/docs/api/refunds).
      console.log("💰 Payment received!");
      return { clientSecret: intent.client_secret };
  }
};

app.listen(4242, (): void =>
  console.log(`Node server listening on port ${4242}!`)
);
