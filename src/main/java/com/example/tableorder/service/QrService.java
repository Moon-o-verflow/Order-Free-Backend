package com.example.tableorder.service;

import com.example.tableorder.dto.QrCodeRequestDto;
import com.example.tableorder.dto.QrCodeResponseDto;
import com.example.tableorder.entity.store.StoreTable;
import com.example.tableorder.exception.NotFoundException;
import com.example.tableorder.exception.InternalErrorException;
import com.example.tableorder.repository.StoreTableRepository;
import com.example.tableorder.util.QrGenerator;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

/**
 * QR 코드 관리 서비스.
 * - 생성/조회, Redis 캐시 지원 (24h).
 * - 소속 검증.
 */
@Service
@RequiredArgsConstructor
public class QrService {

    private static final Logger log = LoggerFactory.getLogger(QrService.class);

    private final StoreTableRepository storeTableRepository;
    private final QrGenerator qrGenerator;
    private final RedisTemplate<String, String> redisTemplate;  // String key/value (Base64 저장)

    private static final String CACHE_PREFIX = "qr:";
    private static final long CACHE_TTL_HOURS = 24;

    /**
     * QR 코드 생성 또는 조회.
     * - 캐시 히트 시 반환, 미스 시 생성/저장.
     * @param storeId 가게 ID
     * @param tableId 테이블 ID
     * @param dto 요청 DTO
     * @return QrCodeResponseDto
     */
    @Transactional(readOnly = true)
    public QrCodeResponseDto getOrCreateQrCode(Long storeId, Long tableId, QrCodeRequestDto dto) {
        StoreTable table = storeTableRepository.findByStore_IdAndId(storeId, tableId)  // findByStoreIdAndId → findByStore_IdAndId로 수정
                .orElseThrow(() -> new NotFoundException("테이블 없음"));

        String cacheKey = CACHE_PREFIX + storeId + ":" + tableId;
        String cachedBase64 = redisTemplate.opsForValue().get(cacheKey);

        if (cachedBase64 != null) {
            log.info("QR 캐시 히트: storeId={}, tableId={}", storeId, tableId);
            return buildResponse(storeId, tableId, dto, cachedBase64, Instant.now().plus(CACHE_TTL_HOURS, ChronoUnit.HOURS));
        }

        String payloadUrl = (dto.getPayloadBaseUrl() != null ? dto.getPayloadBaseUrl() : "https://app.example.com") + "/stores/" + storeId + "/tables/" + tableId;
        int size = dto.getSize() != null ? dto.getSize() : 256;

        String base64;
        try {
            base64 = qrGenerator.generateQrCodeBase64(payloadUrl, size);
        } catch (Exception e) {
            throw new InternalErrorException("QR 생성 실패: " + e.getMessage());
        }

        redisTemplate.opsForValue().set(cacheKey, base64, CACHE_TTL_HOURS, TimeUnit.HOURS);
        log.info("QR 생성 및 캐시: storeId={}, tableId={}", storeId, tableId);

        return buildResponse(storeId, tableId, dto, base64, Instant.now().plus(CACHE_TTL_HOURS, ChronoUnit.HOURS));
    }

    private QrCodeResponseDto buildResponse(Long storeId, Long tableId, QrCodeRequestDto dto, String base64, Instant expiresAt) {
        String payloadUrl = (dto.getPayloadBaseUrl() != null ? dto.getPayloadBaseUrl() : "https://app.example.com") + "/stores/" + storeId + "/tables/" + tableId;
        return QrCodeResponseDto.builder()
                .storeId(storeId)
                .tableId(tableId)
                .payloadUrl(payloadUrl)
                .imageBase64(base64)
                .expiresAt(expiresAt)
                .build();
    }
}