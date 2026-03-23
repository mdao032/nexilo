package com.nexilo.user.dto;

import com.nexilo.user.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {
    UserResponse toResponse(User user);
    
    @Mapping(target = "id", ignore = true)
    User toEntity(UserRequest request);
}

