import React, { useEffect, useState } from 'react';
import { StripeProvider } from 'react-native-stripe-sdk';
import { API_URL } from './Config';
import { Example } from './Example';

export const App: React.FC = () => {
  const [publishableKey, setPublishableKey] = useState('');

  const fetchPublishableKey = async () => {
    const response = await fetch(`${API_URL}/stripe-key`);
    const { publishableKey: key } = await response.json();
    setPublishableKey(key);
  };

  useEffect(() => {
    fetchPublishableKey();
  }, []);

  return (
    <StripeProvider
      publishableKey={publishableKey}
      merchantIdentifier="merchant.com.react.native.stripe.sdk">
      <Example />
    </StripeProvider>
  );
};
