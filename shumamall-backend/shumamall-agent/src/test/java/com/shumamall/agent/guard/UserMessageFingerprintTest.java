package com.shumamall.agent.guard;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserMessageFingerprintTest {

    @Test
    void of_规范化后相同输入得到相同指纹() {
        String a = UserMessageFingerprint.of("  帮我下单  ");
        String b = UserMessageFingerprint.of("帮我下单");
        assertThat(a).isEqualTo(b);
    }

    @Test
    void of_不同输入指纹不同() {
        assertThat(UserMessageFingerprint.of("下单")).isNotEqualTo(UserMessageFingerprint.of("取消"));
    }
}
