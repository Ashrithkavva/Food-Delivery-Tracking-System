import { test } from 'node:test';
import assert from 'node:assert';
import { config } from './config.js';

test('config has an httpPort', () => {
  assert.ok(typeof config.httpPort === 'number');
  assert.ok(config.httpPort > 0);
});

test('config has kafka brokers', () => {
  assert.ok(Array.isArray(config.kafka.brokers));
  assert.ok(config.kafka.brokers.length > 0);
});
