package com.vibecart.api.modules.jobs.config;

import com.vibecart.api.modules.shortener.entity.Commission;
import com.vibecart.api.modules.shortener.entity.CommissionStatus;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public class CommissionRowMapper implements RowMapper<Commission> {
    @Override
    public Commission mapRow(ResultSet rs, int rowNum) throws SQLException {
        Commission commission = new Commission();
        commission.setId(rs.getString("id"));
        commission.setOrderId(rs.getString("order_id"));
        commission.setCreatorId(rs.getString("creator_id"));
        commission.setSubtotalAmount(rs.getBigDecimal("subtotal_amount"));
        commission.setCommissionRate(rs.getBigDecimal("commission_rate"));
        commission.setCommissionAmount(rs.getBigDecimal("commission_amount"));

        String statusStr = rs.getString("status");
        if (statusStr != null) {
            commission.setStatus(CommissionStatus.valueOf(statusStr));
        }

        java.sql.Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) {
            commission.setCreatedAt(ZonedDateTime.ofInstant(ts.toInstant(), ZoneId.systemDefault()));
        }

        String orderStatus = rs.getString("order_status");
        commission.setOrderStatusRaw(orderStatus);

        return commission;
    }
}
