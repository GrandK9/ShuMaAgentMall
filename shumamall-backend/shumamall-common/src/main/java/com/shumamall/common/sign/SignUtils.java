package com.shumamall.common.sign;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * 请求签名工具（HMAC-SHA256）。
 * <p>
 * <b>为什么要做请求签名</b>：HTTPS 只保证传输过程不被中间人窃听/篡改，保护不了「客户端自己发出来的请求」。
 * 攻击者拿到一个合法用户的 token 后，可以用自己的脚本随便构造 body 调下单/支付接口；
 * 即使 token 没过期，服务端也无法区分「用户本人点的」和「别人拿着 token 拼的」。
 * 请求签名把「这次请求的具体内容」和「持有密钥的人」绑在一起：
 * body/参数被改一个字，HMAC 就对不上，服务端直接拒绝。
 * <p>
 * <b>密钥从哪来</b>：不引入第二份长期密钥，而是用 JWT 密钥做带标签的派生（KDF）：
 * <pre>
 *   用户签名密钥 = HMAC-SHA256(jwtSecret, "shumamall-sign-v1:{userId}:{iat}")
 * </pre>
 * <ul>
 *   <li>{@code shumamall-sign-v1} 是域分隔标签，换标签即得到与 JWT 签名密钥完全无关的另一把密钥，
 *       满足密钥分离要求；单份长期密钥也让 s2 定下的「密钥只有配置中心一个来源」继续成立。</li>
 *   <li>掺入 {@code iat}（token 签发时间，秒级）后密钥**每次登录都不同**，退出登录/重新登录即天然轮换，
 *       服务端无需任何存储就能算出同一把密钥（见 {@link SignSecretResolver}）。</li>
 * </ul>
 */
public final class SignUtils {

    /** 派生签名密钥的域分隔标签 */
    private static final String SIGN_LABEL = "shumamall-sign-v1";

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private SignUtils() {
    }

    /**
     * 派生某个用户本次登录的签名密钥。
     *
     * @param jwtSecret             配置中心下发的 JWT 密钥（master key）
     * @param userId                用户 ID
     * @param issuedAtEpochSecond   token 的 iat（秒级），保证同一用户不同登录态密钥不同
     * @return Base64 编码的派生密钥，直接交给客户端保存
     */
    public static String deriveUserSecret(String jwtSecret, Long userId, long issuedAtEpochSecond) {
        String info = SIGN_LABEL + ":" + userId + ":" + issuedAtEpochSecond;
        byte[] key = hmac(jwtSecret.getBytes(StandardCharsets.UTF_8), info.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(key);
    }

    /**
     * 构造待签名原文。客户端与服务端必须按同一套规则拼接，任何一侧多一个空格都会验签失败。
     * <p>
     * 逐行拼接（每项之间 {@code \n}）：
     * <pre>
     *   {METHOD}       大写，如 POST
     *   {PATH}         不含查询串的路径，如 /api/v1/orders
     *   {QUERY}        原始查询串（无则空行），如 status=2&remark=xxx
     *   {TIMESTAMP}    epoch 毫秒
     *   {NONCE}        一次性随机串
     *   {BODY}         原始请求体（无则空行）
     * </pre>
     * 把 METHOD/PATH/QUERY 一起签进去，是为了防止「同一个 body 换个接口或换个查询参数再发一次」——
     * 只签 body 的话，{@code PUT /admin/orders/1/status?status=2} 的签名可以被改成 {@code status=3} 复用。
     *
     * @return 待签名原文
     */
    public static String canonical(String method, String path, String query, String timestamp, String nonce, String body) {
        return join(method == null ? null : method.toUpperCase(), path, query, timestamp, nonce, body);
    }

    /**
     * 用给定密钥对原文做 HMAC-SHA256，输出 Base64。
     *
     * @param secret    密钥（用户的派生签名密钥）
     * @param canonical 待签名原文，见 {@link #canonical}
     * @return Base64 编码的签名
     */
    public static String sign(String secret, String canonical) {
        byte[] raw = hmac(secret.getBytes(StandardCharsets.UTF_8), canonical.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(raw);
    }

    /**
     * 恒定时间比较签名，避免通过响应耗时逐字节猜签名（时序攻击）。
     *
     * @param expected 服务端算出的签名
     * @param actual   客户端提交的签名
     * @return 是否一致
     */
    public static boolean matches(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] hmac(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
            return mac.doFinal(data);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            // JDK 自带 HmacSHA256，密钥也必然合法，这里不可能发生
            throw new IllegalStateException("HMAC-SHA256 计算失败", e);
        }
    }

    private static String join(String... parts) {
        StringBuilder sb = new StringBuilder(128);
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                sb.append('\n');
            }
            if (parts[i] != null) {
                sb.append(parts[i]);
            }
        }
        return sb.toString();
    }
}
