package com.vibecart.api.modules.ecommerce.controller;

import com.vibecart.api.modules.ecommerce.service.OrderService;
import com.vibecart.api.modules.ecommerce.service.PayOSService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentWebhookController {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PaymentWebhookController.class);

    private final PayOSService payOSService;
    private final OrderService orderService;
    @PostMapping("/payos/webhook")
    @SuppressWarnings("unchecked")
    public ResponseEntity<Map<String, Object>> handleWebhook(@RequestBody Map<String, Object> payload) {
        log.info("Received PayOS webhook callback");

        try {
            String receivedSignature = (String) payload.get("signature");
            Map<String, Object> data = (Map<String, Object>) payload.get("data");

            if (data == null || receivedSignature == null) {
                log.warn("Invalid webhook payload: missing data or signature");
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Invalid payload"));
            }


            boolean isValid = payOSService.verifyWebhookSignature(data, receivedSignature);
            if (!isValid) {
                log.error("SECURITY ALERT: Webhook signature verification FAILED. Possible spoofing attack.");
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Invalid signature verification failed"));
            }


            String orderCode = data.get("orderCode").toString();
            String code = data.get("code") != null ? data.get("code").toString() : "";
            String transactionId = data.get("reference") != null ? data.get("reference").toString() : "";


            if ("00".equals(code)) {
                orderService.confirmPayment(orderCode, transactionId, payload.toString());
                log.info("Webhook processed successfully for orderCode: {}", orderCode);
            } else {
                log.warn("Payment not successful for orderCode: {}, code: {}", orderCode, code);
            }


            return ResponseEntity.ok(Map.of("success", true, "message", "Webhook processed successfully"));

        } catch (Exception e) {
            log.error("Error processing webhook: ", e);

            return ResponseEntity.ok(Map.of("success", true, "message", "Error logged"));
        }
    }
}
