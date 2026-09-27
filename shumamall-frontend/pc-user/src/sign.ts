/**
 * 请求签名工具（用户端）。
 *
 * 与后端 `com.shumamall.common.sign.SignUtils` 的规则必须逐字节一致，否则验签必失败：
 *
 *   canonical = METHOD \n PATH \n QUERY \n TIMESTAMP \n NONCE \n BODY
 *   X-Sign    = Base64(HMAC-SHA256(signSecret, canonical))
 *
 * 签名头只加在「会改数据 / 会动钱」的写接口上，读接口不带（后端也只校验写方法）。
 */

/** 需要签名的接口：方法 + 路径（与后端 RequestSignFilter 注册的 URL 模式对应） */
const SIGNED_RULES: Array<{ method: string; pattern: RegExp }> = [
  // order 8084：下单、管理端改订单状态
  { method: 'POST', pattern: /^\/api\/v1\/orders$/ },
  { method: 'PUT', pattern: /^\/api\/v1\/admin\/orders\/\d+\/status$/ },
  // payment 8085：支付、管理端退款
  { method: 'POST', pattern: /^\/api\/v1\/payment\/pay$/ },
  { method: 'POST', pattern: /^\/api\/v1\/admin\/payment\/\d+\/refund$/ },
];

/** 该请求是否需要签名 */
export function isSignedRequest(method: string, url: string): boolean {
  const path = new URL(url, location.origin).pathname;
  return SIGNED_RULES.some((rule) => rule.method === method.toUpperCase() && rule.pattern.test(path));
}

/** 16 字节随机 nonce（hex），与后端 Redis 的「一次性」语义配合防重放 */
function randomNonce(): string {
  const bytes = new Uint8Array(16);
  crypto.getRandomValues(bytes);
  return Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('');
}

/** HMAC-SHA256 → Base64（Web Crypto，localhost / https 下可用） */
async function hmacSha256Base64(secret: string, message: string): Promise<string> {
  const encoder = new TextEncoder();
  const key = await crypto.subtle.importKey(
    'raw',
    encoder.encode(secret),
    { name: 'HMAC', hash: 'SHA-256' },
    false,
    ['sign'],
  );
  const signature = await crypto.subtle.sign('HMAC', key, encoder.encode(message));
  return btoa(String.fromCharCode(...new Uint8Array(signature)));
}

/**
 * 生成签名请求头。
 *
 * @param method HTTP 方法
 * @param url    请求地址（相对路径也可，会基于当前 origin 解析出 path / query）
 * @param body   原始请求体字符串，无 body 传空串
 * @param secret 登录时下发的签名密钥
 */
export async function buildSignHeaders(
  method: string,
  url: string,
  body: string,
  secret: string,
): Promise<Record<string, string>> {
  const parsed = new URL(url, location.origin);
  const timestamp = String(Date.now());
  const nonce = randomNonce();
  const canonical = [
    method.toUpperCase(),
    parsed.pathname,
    parsed.search ? parsed.search.slice(1) : '',
    timestamp,
    nonce,
    body,
  ].join('\n');
  const sign = await hmacSha256Base64(secret, canonical);
  return {
    'X-Timestamp': timestamp,
    'X-Nonce': nonce,
    'X-Sign': sign,
  };
}
