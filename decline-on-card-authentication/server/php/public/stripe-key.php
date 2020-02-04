<?php

require_once 'shared.php';

echo json_encode(['publishableKey' => $config['stripe_publishable_key']]);
