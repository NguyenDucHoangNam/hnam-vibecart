package com.vibecart.api.modules.ecommerce.mapper;

import com.vibecart.api.modules.ecommerce.dto.request.ProductRequest;
import com.vibecart.api.modules.ecommerce.dto.response.ProductImageResponse;
import com.vibecart.api.modules.ecommerce.dto.response.ProductResponse;
import com.vibecart.api.modules.ecommerce.dto.response.ProductVariantResponse;
import com.vibecart.api.modules.ecommerce.entity.Inventory;
import com.vibecart.api.modules.ecommerce.entity.Product;
import com.vibecart.api.modules.ecommerce.entity.ProductImage;
import com.vibecart.api.modules.ecommerce.entity.ProductVariant;
import org.mapstruct.*;

import java.util.List;

@Mapper(componentModel = "spring",
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        builder = @Builder(disableBuilder = true))
public interface ProductMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "category", ignore = true)
    @Mapping(target = "creatorId", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "images", ignore = true)
    @Mapping(target = "variants", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    Product toEntity(ProductRequest request);

    @Mapping(target = "categoryId", source = "category.id")
    @Mapping(target = "categoryName", source = "category.name")
    ProductResponse toResponse(Product product);

    @Mapping(target = "isThumbnail", source = "thumbnail")
    ProductImageResponse toImageResponse(ProductImage image);

    List<ProductImageResponse> toImageResponses(List<ProductImage> images);

    @Mapping(target = "quantity", expression = "java(variant.getInventory() != null ? variant.getInventory().getQuantity() : 0)")
    @Mapping(target = "reservedQuantity", expression = "java(variant.getInventory() != null ? variant.getInventory().getReservedQuantity() : 0)")
    @Mapping(target = "availableStock", expression = "java(variant.getInventory() != null ? variant.getInventory().getQuantity() - variant.getInventory().getReservedQuantity() : 0)")
    ProductVariantResponse toVariantResponse(ProductVariant variant);

    List<ProductVariantResponse> toVariantResponses(List<ProductVariant> variants);
}
