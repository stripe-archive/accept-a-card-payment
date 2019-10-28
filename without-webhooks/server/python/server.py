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
    # Send public key to client
    return jsonify({'publicKey': os.getenv('STRIPE_PUBLIC_KEY')})


@app.route('/pay', methods=['POST'])
def pay():
    data = json.loads(request.data)
    try:
        if "paymentIntentId" not in data:
            order_amount = calculate_order_amount(data['items'])

            # Create new PaymentIntent with a PaymentMethod ID from the client.
            intent = stripe.PaymentIntent.create(
                amount=order_amount,
                currency=data['currency'],
                payment_method=data['paymentMethodId'],
                confirmation_method='manual',
                confirm=True
            )
            # After create, if the PaymentIntent's status is succeeded, fulfill the order.
        else:
            # Confirm the PaymentIntent to finalize payment after handling a required action
            # on the client.
            intent = stripe.PaymentIntent.confirm(data['paymentIntentId'])
            # After confirm, if the PaymentIntent's status is succeeded, fulfill the order.

        return generate_response(intent)
    except Exception as e:
        return jsonify({'error': e})


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
