package com.vibecart.api.modules.search.entity;

import com.vibecart.api.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;

@Entity
@Table(name = "outbox_events")
@Getter
@Setter
public class OutboxEvent extends BaseEntity {

    @Column(name = "aggregate_type", nullable = false, length = 50)
    private String aggregateType; // e.g. "PRODUCT"

    @Column(name = "aggregate_id", nullable = false, length = 36)
    private String aggregateId; // Product SPU ID

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType; // "PRODUCT_CREATED", "PRODUCT_UPDATED", "PRODUCT_DELETED"

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload; // JSON representation of the sync event

    @Column(name = "status", nullable = false, length = 20)
    private String status = "PENDING"; // PENDING, PROCESSED, FAILED

    public OutboxEvent() {}

    public OutboxEvent(String aggregateType, String aggregateId, String eventType, String payload, String status) {
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.status = status != null ? status : "PENDING";
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private String aggregateType;
        private String aggregateId;
        private String eventType;
        private String payload;
        private String status = "PENDING";

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder aggregateType(String aggregateType) {
            this.aggregateType = aggregateType;
            return this;
        }

        public Builder aggregateId(String aggregateId) {
            this.aggregateId = aggregateId;
            return this;
        }

        public Builder eventType(String eventType) {
            this.eventType = eventType;
            return this;
        }

        public Builder payload(String payload) {
            this.payload = payload;
            return this;
        }

        public Builder status(String status) {
            this.status = status;
            return this;
        }

        public OutboxEvent build() {
            OutboxEvent event = new OutboxEvent(aggregateType, aggregateId, eventType, payload, status);
            if (id != null) {
                event.setId(id);
            }
            return event;
        }
    }
}
