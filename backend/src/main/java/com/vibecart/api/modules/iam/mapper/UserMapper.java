package com.vibecart.api.modules.iam.mapper;

import com.vibecart.api.modules.iam.dto.response.UserResponse;
import com.vibecart.api.modules.iam.entity.Role;
import com.vibecart.api.modules.iam.entity.User;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {

    UserResponse toUserResponse(User user);

    default String mapRole(Role role) {
        return role != null ? role.getName() : null;
    }
}
