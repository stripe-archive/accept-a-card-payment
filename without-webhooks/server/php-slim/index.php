<?php
use Psr\Http\Message\ResponseInterface as Response;
use Psr\Http\Message\ServerRequestInterface as Request;
use Stripe\Stripe;
use Psr\Http\Server\RequestHandlerInterface as RequestHandler;
use DI\Container;
use Slim\Factory\AppFactory;

require 'vendor/autoload.php';

$dotenv = \Dotenv\Dotenv::createImmutable(__DIR__);
$dotenv->load();

require './config.php';

if (PHP_SAPI == 'cli-server') {
  $_SERVER['SCRIPT_NAME'] = '/index.php';
}

$container = new Container();
AppFactory::setContainer($container);
$app = AppFactory::create();

// Instantiate the logger as a dependency
$container = $app->getContainer();

$container->set('logger', function ($c) {
  $logger = new Monolog\Logger('stripe');
  $logger->pushProcessor(new Monolog\Processor\UidProcessor());
  $logger->pushHandler(new Monolog\Handler\StreamHandler(__DIR__ . '/logs/app.log', \Monolog\Logger::DEBUG));
  return $logger;
});

$app->add(function (Request $request, RequestHandler $handler) {
    Stripe::setApiKey($_ENV['STRIPE_SECRET_KEY']);
    return $handler->handle($request);
});


$app->get('/', function (Request $request, Response $response, array $args) {
  // Display checkout page
  $response->getBody()->write(file_get_contents($_ENV['STATIC_DIR'] . '/index.html'));

  return $response;
});

function calculateOrderAmount($items)
{
  // Replace this constant with a calculation of the order's amount
  // You should always calculate the order total on the server to prevent
  // people from directly manipulating the amount on the client
  return 1400;
}

function generateResponse($intent, $logger) 
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
        'error' => "Your card was denied, please provide a new payment method"
      ];
    case "succeeded":
      // Payment is complete, authentication not required
      // To cancel the payment after capture you will need to issue a Refund (https://stripe.com/docs/api/refunds)
      $logger->info("💰 Payment received!");
      return ['clientSecret' => $intent->client_secret];
  }

  throw new \Exception('unsupported status');
}

$app->get('/stripe-key', function (Request $request, Response $response, array $args) {
    $pubKey = $_ENV['STRIPE_PUBLISHABLE_KEY'];
    $response
        ->withHeader('Content-Type', 'application/json')
        ->getBody()
        ->write(json_encode(['publishableKey' => $pubKey]))
    ;
    return $response;
});


$app->post('/pay', function(Request $request, Response $response) use ($app)  {
  $logger = $this->get('logger');
  $body = json_decode($request->getBody(), true);
  try {
    if($body['paymentMethodId'] != null) {
      // Create new PaymentIntent with a PaymentMethod ID from the client.
      $intent = \Stripe\PaymentIntent::create([
        "amount" => calculateOrderAmount($body['items']),
        "currency" => $body['currency'],
        "payment_method" => $body['paymentMethodId'],
        "confirmation_method" => "manual",
        "confirm" => true,
        // 3D Payment is not supported by this script, but the return_url is required in many cases.
        "return_url"=> "http://localhost:4242/",
        // If a mobile client passes `useStripeSdk`, set `use_stripe_sdk=true`
        // to take advantage of new authentication features in mobile SDKs
        "use_stripe_sdk" => $body['useStripeSdk'] ?? false,
      ]);
      // After create, if the PaymentIntent's status is succeeded, fulfill the order.
    } else if ($body['paymentIntentId'] !== null) {
      // Confirm the PaymentIntent to finalize payment after handling a required action
      // on the client.
      $intent = \Stripe\PaymentIntent::retrieve($body['paymentIntentId']);
      $intent->confirm();
      // After confirm, if the PaymentIntent's status is succeeded, fulfill the order.
    }  
    $responseBody = generateResponse($intent, $logger);
    $response->withHeader('Content-Type', 'application/json')->getBody()->write(json_encode($responseBody));
  } catch (\Stripe\Exception\ApiErrorException $e) {
    # Display error on client
    $response->withHeader('Content-Type', 'application/json')
        ->getBody()
        ->write(json_encode([
          'error' => $e->getMessage()
        ]));
  }
  return $response;
});

$app->run();
