package com.vibecart.api.modules.iam.mapper;

import com.vibecart.api.modules.iam.dto.response.UserResponse;
import com.vibecart.api.modules.iam.entity.Role;
import com.vibecart.api.modules.iam.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "hasPassword", expression = "java(user.getPassword() != null)")
    UserResponse toUserResponse(User user);

    default String mapRole(Role role) {
        return role != null ? role.getName() : null;
    }
}
