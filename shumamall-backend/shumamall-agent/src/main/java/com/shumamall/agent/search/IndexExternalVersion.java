package com.shumamall.agent.search;

/**
 * 全量灌库与增量双写共用的外部版本号。
 * <p>
 * 同一毫秒内，增量版本比全量快照版本大 1，避免后发出的旧页覆盖刚写入的增量。
 */
public final class IndexExternalVersion {

    private IndexExternalVersion() {
    }

    /** 全量某一页：版本号取拉取该页的时刻。 */
    public static long snapshot(long fetchedAtMillis) {
        return fetchedAtMillis * 2;
    }

    /** 增量写入：同一毫秒内高于快照版本。 */
    public static long live(long nowMillis) {
        return nowMillis * 2 + 1;
    }
}
