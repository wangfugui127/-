package com.lsy.minimall.persistence.mapper;

import java.time.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface ClockMapper {
  @Select("SELECT UTC_TIMESTAMP(3)")
  LocalDateTime currentUtcTime();

  default Instant now() {
    return currentUtcTime().toInstant(ZoneOffset.UTC);
  }
}
