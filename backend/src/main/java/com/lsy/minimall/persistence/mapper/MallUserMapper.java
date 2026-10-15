package com.lsy.minimall.persistence.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lsy.minimall.persistence.MapperRows;
import com.lsy.minimall.persistence.entity.MallUser;
import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface MallUserMapper extends BaseMapper<MallUser> {
  default long createUser(Object username, Object passwordHash) {
    MallUser entity = new MallUser();
    entity.username = MapperRows.text(username);
    entity.passwordHash = MapperRows.text(passwordHash);
    insert(entity);
    return Objects.requireNonNull(entity.id);
  }

  default Map<String, Object> findByUsername(Object username) {
    return MapperRows.first(selectMaps(new QueryWrapper<MallUser>().eq("username", username)));
  }

  default Map<String, Object> findProfileById(Object id) {
    return MapperRows.first(
        selectMaps(
            new QueryWrapper<MallUser>().select("id", "username", "role", "status").eq("id", id)));
  }

  default long countAdministrators() {
    return selectCount(new QueryWrapper<MallUser>().eq("username", "admin"));
  }

  default int createAdministrator(Object passwordHash) {
    MallUser entity = new MallUser();
    entity.username = MapperRows.text("admin");
    entity.passwordHash = MapperRows.text(passwordHash);
    entity.role = MapperRows.text("ADMIN");
    return insert(entity);
  }

  default Map<String, Object> findAdministratorIdentity() {
    return MapperRows.first(
        selectMaps(new QueryWrapper<MallUser>().select("id").eq("username", "admin")));
  }

  default Map<String, Object> findIdentityById(Object id) {
    return MapperRows.first(
        selectMaps(new QueryWrapper<MallUser>().select("id", "role", "status").eq("id", id)));
  }
}
