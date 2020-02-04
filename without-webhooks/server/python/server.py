#! /usr/bin/env python3.6

"""
server.py
Stripe Sample.
Python 3.6 or newer required.
"""

import stripe
import json
import os

from flask import Flask, render_template, jsonify, request, send_from_directory
from dotenv import load_dotenv, find_dotenv

# Setup Stripe python client library
load_dotenv(find_dotenv())
stripe.api_key = os.getenv('STRIPE_SECRET_KEY')
stripe.api_version = os.getenv('STRIPE_API_VERSION')

static_dir = str(os.path.abspath(os.path.join(__file__ , "..", os.getenv("STATIC_DIR"))))
app = Flask(__name__, static_folder=static_dir,
            static_url_path="", template_folder=static_dir)

@app.route('/', methods=['GET'])
def get_example():
    # Display checkout page
    return render_template('index.html')


def calculate_order_amount(items):
    # Replace this constant with a calculation of the order's amount
    # Calculate the order total on the server to prevent
    # people from directly manipulating the amount on the client
    return 1400


@app.route('/stripe-key', methods=['GET'])
def fetch_key():
    # Send publishable key to client
    return jsonify({'publishableKey': os.getenv('STRIPE_PUBLISHABLE_KEY')})


@app.route('/pay', methods=['POST'])
def pay():
    data = request.get_json()
    try:
        if 'paymentMethodId' in data:
            order_amount = calculate_order_amount(data['items'])

            # Create new PaymentIntent with a PaymentMethod ID from the client.
            intent = stripe.PaymentIntent.create(
                amount=order_amount,
                currency=data['currency'],
                payment_method=data['paymentMethodId'],
                confirmation_method='manual',
                confirm=True,
                # If a mobile client passes `useStripeSdk`, set `use_stripe_sdk=true`
                # to take advantage of new authentication features in mobile SDKs.
                use_stripe_sdk=True if 'useStripeSdk' in data and data['useStripeSdk'] else None,
            )
            # After create, if the PaymentIntent's status is succeeded, fulfill the order.
        elif 'paymentIntentId' in data:
            # Confirm the PaymentIntent to finalize payment after handling a required action
            # on the client.
            intent = stripe.PaymentIntent.confirm(data['paymentIntentId'])
            # After confirm, if the PaymentIntent's status is succeeded, fulfill the order.

        return generate_response(intent)
    except stripe.error.CardError as e:
        return jsonify({'error': e.user_message})


def generate_response(intent):
    status = intent['status']
    if status == 'requires_action' or status == 'requires_source_action':
        # Card requires authentication
        return jsonify({'requiresAction': True, 'paymentIntentId': intent['id'], 'clientSecret': intent['client_secret']})
    elif status == 'requires_payment_method' or status == 'requires_source':
        # Card was not properly authenticated, suggest a new payment method
        return jsonify({'error': 'Your card was denied, please provide a new payment method'})
    elif status == 'succeeded':
        # Payment is complete, authentication not required
        # To cancel the payment you will need to issue a Refund (https://stripe.com/docs/api/refunds)
        print("💰 Payment received!")
        return jsonify({'clientSecret': intent['client_secret']})


if __name__ == '__main__':
    app.run()
