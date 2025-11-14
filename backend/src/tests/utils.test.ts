import { verifyWebhookHmac } from '../utils';


describe('utils.verifyWebhookHmac', () => {
it('validates correct signature', () => {
const secret = 'test-secret';
const payload = JSON.stringify({ a: 1 });
const crypto = require('crypto');
const h = crypto.createHmac('sha256', secret).update(payload).digest('hex');
expect(verifyWebhookHmac(payload, h, secret)).toBe(true);
expect(verifyWebhookHmac(payload, `sha256=${h}`, secret)).toBe(true);
});


it('rejects bad signature', () => {
const secret = 'test-secret';
const payload = JSON.stringify({ a: 1 });
expect(verifyWebhookHmac(payload, 'bad', secret)).toBe(false);
});
});