package com.example.tableorder.service.payment;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * PG(토스페이먼츠)에 넘길 주문번호. 6~64자, 영문·숫자·'-'·'_' 만 허용된다.
 * 형식: OF-{주문ID}-{무작위 16자}. 재시도마다 새로 발급한다.
 */
@Component
public class PgOrderIdGenerator {

    private static final char[] ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".toCharArray();
    private final SecureRandom random = new SecureRandom();

    public String next(Long orderId) {
        StringBuilder sb = new StringBuilder("OF-").append(orderId).append('-');
        for (int i = 0; i < 16; i++) {
            sb.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        }
        return sb.toString();
    }
}
