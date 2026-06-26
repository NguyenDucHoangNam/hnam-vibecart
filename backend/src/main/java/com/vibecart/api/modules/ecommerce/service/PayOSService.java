package com.vibecart.api.modules.ecommerce.service;

import com.vibecart.api.common.exception.AppException;
import com.vibecart.api.common.exception.ErrorCode;
import com.vibecart.api.config.PayOSProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;

/**
 * Service tích hợp cổng thanh toán PayOS: tạo link, xác thực webhook.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PayOSService {

    private final PayOSProperties payOSProperties;
    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Create a payment link via PayOS API.
     * This method is called OUTSIDE of the database transaction.
     */
    @SuppressWarnings("unchecked")
    public String createPaymentLink(long orderCode, long amount, String description) {
        log.info("Creating PayOS payment link for orderCode: {}, amount: {}", orderCode, amount);

        try {

            String signatureData = "amount=" + amount
                    + "&cancelUrl=" + payOSProperties.getCancelUrl()
                    + "&description=" + description
                    + "&orderCode=" + orderCode
                    + "&returnUrl=" + payOSProperties.getReturnUrl();

            String signature = hmacSHA256(payOSProperties.getChecksumKey(), signatureData);


            Map<String, Object> body = Map.of(
                    "orderCode", orderCode,
                    "amount", amount,
                    "description", description,
                    "cancelUrl", payOSProperties.getCancelUrl(),
                    "returnUrl", payOSProperties.getReturnUrl(),
                    "signature", signature
            );


            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("x-client-id", payOSProperties.getClientId());
            headers.set("x-api-key", payOSProperties.getApiKey());

            HttpEntity<Map<String, Object>> httpEntity = new HttpEntity<>(body, headers);

            ResponseEntity<Map> responseEntity = restTemplate.exchange(
                    payOSProperties.getBaseUrl() + "/v2/payment-requests",
                    HttpMethod.POST,
                    httpEntity,
                    Map.class
            );

            Map<String, Object> responseBody = responseEntity.getBody();
            if (responseBody != null && "00".equals(responseBody.get("code").toString())) {
                Map<String, Object> data = (Map<String, Object>) responseBody.get("data");
                String checkoutUrl = (String) data.get("checkoutUrl");
                log.info("PayOS payment link created: {}", checkoutUrl);
                return checkoutUrl;
            } else {
                log.error("PayOS payment creation failed: {}", responseBody);
                throw new AppException(ErrorCode.PAYMENT_GATEWAY_ERROR);
            }
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.error("PayOS API call failed: ", e);
            throw new AppException(ErrorCode.PAYMENT_GATEWAY_ERROR);
        }
    }

    /**
     * Verify webhook signature from PayOS using HMAC-SHA256.
     */
    public boolean verifyWebhookSignature(Map<String, Object> data, String receivedSignature) {
        try {

            TreeMap<String, Object> sortedData = new TreeMap<>(data);


            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, Object> entry : sortedData.entrySet()) {
                if (!sb.isEmpty()) {
                    sb.append("&");
                }
                sb.append(entry.getKey()).append("=").append(entry.getValue());
            }

            String computedSignature = hmacSHA256(payOSProperties.getChecksumKey(), sb.toString());
            boolean isValid = computedSignature.equals(receivedSignature);

            if (!isValid) {
                log.warn("SECURITY ALERT: Invalid webhook signature detected! Expected: {}, Received: {}",
                        computedSignature, receivedSignature);
            }

            return isValid;
        } catch (Exception e) {
            log.error("Webhook signature verification failed: ", e);
            return false;
        }
    }

    /**
     * HMAC-SHA256 hash function.
     */
    private String hmacSHA256(String key, String data) throws Exception {
        Mac hmac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        hmac.init(secretKey);
        byte[] hash = hmac.doFinal(data.getBytes(StandardCharsets.UTF_8));

        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
