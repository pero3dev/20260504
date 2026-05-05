package com.example.inventory.identity.adapter.out.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMapper {

    UserRow findByEmail(@Param("email") String email);

    UserRow findById(@Param("id") long id);
}
