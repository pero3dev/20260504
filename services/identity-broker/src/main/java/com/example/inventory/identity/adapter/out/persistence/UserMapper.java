package com.example.inventory.identity.adapter.out.persistence;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMapper {

    UserRow findByEmail(@Param("email") String email);

    UserRow findById(@Param("id") long id);

    List<UserRow> findAll();

    void insert(@Param("row") UserRow row);
}
