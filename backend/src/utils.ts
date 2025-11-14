import crypto from 'crypto';


export function verifyWebhookHmac(payload: string, signature: string | undefined, secret: string) {
if (!signature) return false;
const h = crypto.createHmac('sha256', secret).update(payload).digest('hex');
return signature === h || signature === `sha256=${h}`;
}


export function isValidUuid(s: string) {
return /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/.test(s);
}