import path from "path";
import env from "dotenv";
import express, { Response, Request } from "express";
import bodyParser from "body-parser";
import Stripe from "stripe";

// Replace if using a different env file or config
env.config({ path: "./.env" });

const stripe = new Stripe(process.env.STRIPE_SECRET_KEY, {
  apiVersion: "2019-12-03",
  typescript: true
});

const app = express();
const resolve = path.resolve;

app.use(express.static(process.env.STATIC_DIR));
app.use(
  (
    req: express.Request,
    res: express.Response,
    next: express.NextFunction
  ): void => {
    if (req.originalUrl === "/webhook") {
      next();
    } else {
      bodyParser.json()(req, res, next);
    }
  }
);

app.get("/checkout", (req: Request, res: Response) => {
  // Display checkout page
  const path = resolve(process.env.STATIC_DIR + "/index.html");
  res.sendFile(path);
});

interface Order {
  items: Array<Object>;
}

const calculateOrderAmount = (order: Order): number => {
  // Replace this constant with a calculation of the order's amount
  // Calculate the order total on the server to prevent
  // people from directly manipulating the amount on the client
  return 1400;
};

app.post("/create-payment-intent", async (req: Request, res: Response) => {
  const { items, currency } = req.body;
  // Create a PaymentIntent with the order amount and currency
  const params: Stripe.PaymentIntentCreateParams = {
    amount: calculateOrderAmount(items),
    currency: currency
  };

  const paymentIntent: Stripe.PaymentIntent = await stripe.paymentIntents.create(
    params
  );

  // Send publishable key and PaymentIntent details to client
  res.send({
    publishableKey: process.env.STRIPE_PUBLISHABLE_KEY,
    clientSecret: paymentIntent.client_secret
  });
});

// Expose a endpoint as a webhook handler for asynchronous events.
// Configure your webhook in the stripe developer dashboard
// https://dashboard.stripe.com/test/webhooks
app.post(
  "/webhook",
  bodyParser.raw({ type: "application/json" }),
  async (req: Request, res: Response) => {
    let data: Stripe.Event.Data, eventType: string;

    // Check if webhook signing is configured.
    if (process.env.STRIPE_WEBHOOK_SECRET) {
      // Retrieve the event by verifying the signature using the raw body and secret.
      let event: Stripe.Event;
      let signature = req.headers["stripe-signature"];

      try {
        event = stripe.webhooks.constructEvent(
          req.body,
          signature,
          process.env.STRIPE_WEBHOOK_SECRET
        );
      } catch (err) {
        console.log(`⚠️  Webhook signature verification failed.`);
        return res.sendStatus(400);
      }
      data = event.data;
      eventType = event.type;
    } else {
      // Webhook signing is recommended, but if the secret is not configured in `config.js`,
      // we can retrieve the event data directly from the request body.
      data = req.body.data;
      eventType = req.body.type;
    }

    if (eventType === "payment_intent.succeeded") {
      // Funds have been captured
      // Fulfill any orders, e-mail receipts, etc
      // To cancel the payment after capture you will need to issue a Refund (https://stripe.com/docs/api/refunds)
      console.log("💰 Payment captured!");
    } else if (eventType === "payment_intent.payment_failed") {
      console.log("❌ Payment failed.");
    }
    res.sendStatus(200);
  }
);

app.listen(4242, () => console.log(`Node server listening on port ${4242}!`));
