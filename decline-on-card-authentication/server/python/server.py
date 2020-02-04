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
        order_amount = calculate_order_amount(data['items'])

        # Create new PaymentIntent with a PaymentMethod ID from the client.
        intent = stripe.PaymentIntent.create(
            amount=order_amount,
            currency=data['currency'],
            payment_method=data['paymentMethodId'],
            error_on_requires_action=True,
            confirm=True,
        )
        print("💰 Payment received!")
        # The payment is complete and the money has been moved
        # You can add any post-payment code here (e.g. shipping, fulfillment, etc)

        # Send the client secret to the client to use in the demo
        return jsonify({'clientSecret': intent['client_secret']})
    except stripe.error.CardError as e:
        if e.code == 'authentication_required':
            return jsonify({'error': 'This card requires authentication in order to proceeded. Please use a different card'})
        else:
            return jsonify({'error': e.user_message})

if __name__ == '__main__':
    app.run()
