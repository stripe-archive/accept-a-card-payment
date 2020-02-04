# frozen_string_literal: true

require 'stripe'
require 'sinatra'
require 'dotenv'

# Replace if using a different env file or config
Dotenv.load
Stripe.api_key = ENV['STRIPE_SECRET_KEY']

set :static, true
set :public_folder, File.join(File.dirname(__FILE__), ENV['STATIC_DIR'])
set :port, 4242

get '/' do
  # Display checkout page
  content_type 'text/html'
  send_file File.join(settings.public_folder, 'index.html')
end

def calculate_order_amount(_items)
  # Replace this constant with a calculation of the order's amount
  # Calculate the order total on the server to prevent
  # people from directly manipulating the amount on the client
  1400
end

get '/stripe-key' do
  content_type 'application/json'
  # Send publishable key to client
  {
    publishableKey: ENV['STRIPE_PUBLISHABLE_KEY']
  }.to_json
end

post '/pay' do
  data = JSON.parse(request.body.read)
  order_amount = calculate_order_amount(data['items'])

  begin
    # Create a PaymentIntent with a PaymentMethod ID from the client.
    intent = Stripe::PaymentIntent.create(
      amount: order_amount,
      currency: data['currency'],
      payment_method: data['paymentMethodId'],
      error_on_requires_action: true,
      confirm: true
    )

    puts '💰 Payment received!'
    # The payment is complete and the money has been moved
    # You can add any post-payment code here (e.g. shipping, fulfillment, etc)

    # Send the client secret to the client to use in the demo
    {
      clientSecret: intent['client_secret']
    }.to_json
  rescue Stripe::CardError => e
    content_type 'application/json'

    if e.code == 'authentication_required'
      {
        error: 'This card requires authentication in order to proceeded. Please use a different card' 
      }.to_json
    else
      {
        error: e.message
      }.to_json
    end
  end
end
