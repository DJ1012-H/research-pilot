package com.dj1012h.researchpilot.mapper;

import org.apache.ibatis.annotations.Select;

public interface DatabaseProbeMapper {

    @Select("SELECT 1")
    Integer selectOne();
}
