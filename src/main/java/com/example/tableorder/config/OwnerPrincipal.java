package com.example.tableorder.config;

import java.util.Set;

/**
 * JWT로 인증된 점주 정보.
 *
 * @param ownerId  토큰 subject
 * @param storeIds 토큰의 storeIds 클레임 — 이 점주가 관리할 수 있는 매장 ID 목록
 */
public record OwnerPrincipal(String ownerId, Set<Long> storeIds) {

    public OwnerPrincipal {
        storeIds = storeIds == null ? Set.of() : Set.copyOf(storeIds);
    }

    public boolean owns(Long storeId) {
        return storeId != null && storeIds.contains(storeId);
    }

    @Override
    public String toString() {
        return ownerId;
    }
}
