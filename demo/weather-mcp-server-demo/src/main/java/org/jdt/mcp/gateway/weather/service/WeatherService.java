package org.jdt.mcp.gateway.weather.service;


import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class WeatherService {

    // todo 能否提供流式返回
    // todo 调整mcp服务表的字段描述和mcp区分开
    @Tool(description = "根据城市名查询当前天气")
    public String getTemp(@ToolParam(description = "输入城市名") String city){
        return city+"现在42摄氏度，注意保暖";
    }

}
