package com.vibecart.api.modules.iam.mapper;

import com.vibecart.api.modules.iam.dto.response.UserResponse;
import com.vibecart.api.modules.iam.entity.Role;
import com.vibecart.api.modules.iam.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "hasPassword", expression = "java(user.getPassword() != null)")
    @Mapping(target = "roles", expression = "java(user.getRole() != null ? java.util.Set.of(user.getRole().getName()) : java.util.Collections.emptySet())")
    UserResponse toUserResponse(User user);
}
