package com.vibecart.api.modules.jobs.config;

import com.vibecart.api.modules.shortener.entity.Commission;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * RowMapper chuyển đổi kết quả JDBC thành entity Commission.
 */
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
        commission.setStatus(rs.getString("status"));

        java.sql.Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) {
            commission.setCreatedAt(ZonedDateTime.ofInstant(ts.toInstant(), ZoneId.systemDefault()));
        }
        return commission;
    }
}
