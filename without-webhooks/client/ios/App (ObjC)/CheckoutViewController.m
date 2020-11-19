//
//  CheckoutViewController.m
//  app
//
//  Created by Ben Guo on 9/29/19.
//  Copyright © 2019 stripe-samples. All rights reserved.
//

#import "CheckoutViewController.h"
@import Stripe;

/**
* This example collects card payments, implementing the guide here: https://stripe.com/docs/payments/accept-a-payment-synchronously#ios
* 
* To run this app, follow the steps here https://github.com/stripe-samples/accept-a-card-payment#how-to-run-locally
*/
NSString *const BackendUrl = @"http://127.0.0.1:4242/";

@interface CheckoutViewController ()  <STPAuthenticationContext>

@property (weak) STPPaymentCardTextField *cardTextField;
@property (weak) UIButton *payButton;

@end

@implementation CheckoutViewController

- (void)viewDidLoad {
    [super viewDidLoad];
    self.view.backgroundColor = [UIColor whiteColor];

    STPPaymentCardTextField *cardTextField = [[STPPaymentCardTextField alloc] init];
    self.cardTextField = cardTextField;

    UIButton *button = [UIButton buttonWithType:UIButtonTypeCustom];
    button.layer.cornerRadius = 5;
    button.backgroundColor = [UIColor systemBlueColor];
    button.titleLabel.font = [UIFont systemFontOfSize:22];
    [button setTitle:@"Pay" forState:UIControlStateNormal];
    [button addTarget:self action:@selector(pay) forControlEvents:UIControlEventTouchUpInside];
    self.payButton = button;

    UIStackView *stackView = [[UIStackView alloc] initWithArrangedSubviews:@[cardTextField, button]];
    stackView.axis = UILayoutConstraintAxisVertical;
    stackView.translatesAutoresizingMaskIntoConstraints = NO;
    stackView.spacing = 20;
    [self.view addSubview:stackView];

    [NSLayoutConstraint activateConstraints:@[
        [stackView.leftAnchor constraintEqualToSystemSpacingAfterAnchor:self.view.leftAnchor multiplier:2],
        [self.view.rightAnchor constraintEqualToSystemSpacingAfterAnchor:stackView.rightAnchor multiplier:2],
        [stackView.topAnchor constraintEqualToSystemSpacingBelowAnchor:self.view.topAnchor multiplier:2],
    ]];

    [self startCheckout];
}

- (void)displayAlertWithTitle:(NSString *)title message:(NSString *)message restartDemo:(BOOL)restartDemo {
    dispatch_async(dispatch_get_main_queue(), ^{
        UIAlertController *alert = [UIAlertController alertControllerWithTitle:title message:message preferredStyle:UIAlertControllerStyleAlert];
        if (restartDemo) {
            [alert addAction:[UIAlertAction actionWithTitle:@"Restart demo" style:UIAlertActionStyleCancel handler:^(UIAlertAction *action) {
                [self.cardTextField clear];
                [self startCheckout];
            }]];
        }
        else {
            [alert addAction:[UIAlertAction actionWithTitle:@"OK" style:UIAlertActionStyleCancel handler:nil]];
        }
        [self presentViewController:alert animated:YES completion:nil];
    });
}

- (void)startCheckout {
    // For added security, our sample app gets the publishable key from the server
    NSURL *url = [NSURL URLWithString:[NSString stringWithFormat:@"%@stripe-key", BackendUrl]];
    NSMutableURLRequest *request = [[NSURLRequest requestWithURL:url] mutableCopy];
    [request setHTTPMethod:@"GET"];
    [request setValue:@"application/json" forHTTPHeaderField:@"Content-Type"];
    NSURLSessionTask *task = [[NSURLSession sharedSession] dataTaskWithRequest:request completionHandler:^(NSData *data, NSURLResponse *response, NSError *requestError) {
        NSError *error = requestError;
        NSDictionary *json = [NSJSONSerialization JSONObjectWithData:data options:0 error:&error];
        NSHTTPURLResponse *httpResponse = (NSHTTPURLResponse *)response;
        if (error != nil || httpResponse.statusCode != 200 || json[@"publishableKey"] == nil) {
            [self displayAlertWithTitle:@"Error loading page" message:error.localizedDescription ?: @"" restartDemo:NO];
        }
        else {
            NSLog(@"Loaded Stripe key");
            // Configure the SDK with your Stripe publishable key so that it can make requests to the Stripe API
            NSString *publishableKey = json[@"publishableKey"];
            [StripeAPI setDefaultPublishableKey:publishableKey];
        }
    }];
    [task resume];
}

- (void)pay {
    // Collect card details on the client
    STPPaymentMethodCardParams *cardParams = self.cardTextField.cardParams;
    STPPaymentMethodParams *paymentMethodParams = [STPPaymentMethodParams paramsWithCard:cardParams billingDetails:nil metadata:nil];
    [[STPAPIClient sharedClient] createPaymentMethodWithParams:paymentMethodParams completion:^(STPPaymentMethod *paymentMethod, NSError *createError) {
        // Create PaymentMethod failed
        if (createError != nil) {
            [self displayAlertWithTitle:@"Payment failed" message:createError.localizedDescription ?: @"" restartDemo:NO];
        }
        else if (paymentMethod != nil) {
            // Create a PaymentIntent on the server with a PaymentMethod
            NSLog(@"Created PaymentMethod");
            [self payWithPaymentMethod:paymentMethod.stripeId orPaymentIntent:nil];
        }
    }];
}

// Create or confirm a PaymentIntent on the server
- (void)payWithPaymentMethod:(NSString *)paymentMethodId orPaymentIntent:(NSString *)paymentIntentId {
    NSDictionary *json = @{};
    if (paymentMethodId != nil) {
        json = @{
            @"useStripeSdk": @YES,
            @"paymentMethodId": paymentMethodId,
            @"currency": @"usd",
            @"items": @[
                    @{@"id": @"photo_subscription"}
            ]
        };
    }
    else {
        json = @{
            @"paymentIntentId": paymentIntentId,
        };
    }
    NSURL *url = [NSURL URLWithString:[NSString stringWithFormat:@"%@pay", BackendUrl]];
    NSData *body = [NSJSONSerialization dataWithJSONObject:json options:0 error:nil];
    NSMutableURLRequest *request = [[NSURLRequest requestWithURL:url] mutableCopy];
    [request setHTTPMethod:@"POST"];
    [request setValue:@"application/json" forHTTPHeaderField:@"Content-Type"];
    [request setHTTPBody:body];
    NSURLSessionTask *task = [[NSURLSession sharedSession] dataTaskWithRequest:request completionHandler:^(NSData *data, NSURLResponse *response, NSError *requestError) {
        NSError *error = requestError;
        NSDictionary *json = [NSJSONSerialization JSONObjectWithData:data options:0 error:&error];
        NSHTTPURLResponse *httpResponse = (NSHTTPURLResponse *)response;
        // Request failed
        if (error != nil || httpResponse.statusCode != 200) {
            [self displayAlertWithTitle:@"Payment failed" message:error.localizedDescription ?: @"" restartDemo:NO];
        }
        else {
            NSNumber *requiresAction = json[@"requiresAction"];
            NSString *clientSecret = json[@"clientSecret"];
            NSString *payError = json[@"error"];
            // Payment failed
            if (payError != nil) {
                [self displayAlertWithTitle:@"Payment failed" message:payError restartDemo:NO];
            }
            // Payment succeeded
            else if (clientSecret != nil && (requiresAction == nil || [requiresAction isEqualToNumber:@NO])) {
                [self displayAlertWithTitle:@"Payment succeeded" message:clientSecret restartDemo:YES];
            }
            // Payment requires additional actions
            else if (clientSecret != nil && [requiresAction isEqualToNumber:@YES]) {
                STPPaymentHandler *paymentHandler = [STPPaymentHandler sharedHandler];
                [paymentHandler handleNextActionForPayment:clientSecret withAuthenticationContext:self returnURL:nil completion:^(STPPaymentHandlerActionStatus status, STPPaymentIntent *paymentIntent, NSError *handleActionError) {
                        switch (status) {
                            case STPPaymentHandlerActionStatusFailed: {
                                [self displayAlertWithTitle:@"Payment failed" message:handleActionError.localizedDescription ?: @"" restartDemo:NO];
                                break;
                            }
                            case STPPaymentHandlerActionStatusCanceled: {
                                [self displayAlertWithTitle:@"Payment canceled" message:handleActionError.localizedDescription ?: @"" restartDemo:NO];
                                break;
                            }
                            case STPPaymentHandlerActionStatusSucceeded: {
                                // After handling a required action on the client, the status of the PaymentIntent is
                                // requires_confirmation. You must send the PaymentIntent ID to your backend
                                // and confirm it to finalize the payment. This step enables your integration to
                                // synchronously fulfill the order on your backend and return the fulfillment result
                                // to your client.
                                if (paymentIntent.status == STPPaymentIntentStatusRequiresConfirmation) {
                                    NSLog(@"Re-confirming PaymentIntent after handling action");
                                    [self payWithPaymentMethod:nil orPaymentIntent:paymentIntent.stripeId];
                                }
                                else {
                                    [self displayAlertWithTitle:@"Payment succeeded" message:paymentIntent.description restartDemo:YES];
                                }
                                break;
                            }
                            default:
                                break;
                        }
                }];
            }
        }
    }];
    [task resume];
}

# pragma mark STPAuthenticationContext
- (UIViewController *)authenticationPresentingViewController {
    return self;
}

@end
