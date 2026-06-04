package com.vibecart.api.modules.ecommerce.mapper;

import com.vibecart.api.modules.ecommerce.dto.response.OrderItemResponse;
import com.vibecart.api.modules.ecommerce.dto.response.OrderResponse;
import com.vibecart.api.modules.ecommerce.entity.Order;
import com.vibecart.api.modules.ecommerce.entity.OrderItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface OrderMapper {

    @Mapping(target = "orderId", source = "id")
    @Mapping(target = "paymentUrl", source = "paymentLinkId")
    @Mapping(target = "creatorName", ignore = true)
    OrderResponse toResponse(Order order);

    OrderItemResponse toItemResponse(OrderItem item);

    List<OrderItemResponse> toItemResponses(List<OrderItem> items);
}
