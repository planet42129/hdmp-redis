package com.hmdp.dto;

import lombok.Data;

import java.util.List;

//关注推送里的返回结果
@Data
public class ScrollResult {
    private List<?> list;
    private Long minTime;
    private Integer offset;
}
