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
      items,
      currency
    }: {
      paymentMethodId: string;
      items: Order;
      currency: string;
    } = req.body;

    const orderAmount: number = calculateOrderAmount(items);

    try {
      // Create new PaymentIntent with a PaymentMethod ID from the client.
      const params: Stripe.PaymentIntentCreateParams = {
        amount: orderAmount,
        confirm: true,
        error_on_requires_action: true,
        currency,
        payment_method: paymentMethodId
      };

      const intent: Stripe.PaymentIntent = await stripe.paymentIntents.create(
        params
      );

      console.log("💰 Payment received!");
      // The payment is complete and the money has been moved
      // You can add any post-payment code here (e.g. shipping, fulfillment, etc)

      // Send the client secret to the client to use in the demo
      res.send({ clientSecret: intent.client_secret });
    } catch (e) {
      // Handle "hard declines" e.g. insufficient funds, expired card, etc
      // See https://stripe.com/docs/declines/codes for more.
      if (e.code === "authentication_required") {
        res.send({
          error:
            "This card requires authentication in order to proceeded. Please use a different card."
        });
      } else {
        res.send({ error: e.message });
      }
    }
  }
);

app.listen(4242, (): void =>
  console.log(`Node server listening on port ${4242}!`)
);
