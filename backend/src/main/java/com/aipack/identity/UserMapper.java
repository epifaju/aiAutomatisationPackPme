package com.aipack.identity;

import com.aipack.auth.dto.UserMeResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "companyId", source = "company.id")
    @Mapping(target = "companyName", source = "company.name")
    UserMeResponse toMeResponse(User user);
}
