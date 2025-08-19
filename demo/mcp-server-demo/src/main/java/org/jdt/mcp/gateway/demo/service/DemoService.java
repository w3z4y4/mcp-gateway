package org.jdt.mcp.gateway.demo.service;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

@Service
public class DemoService {
    @Tool(description = "根据姓名查询工作单位")
    public String getJobs(@ToolParam(description = "姓名") String name){
        return name+"在工商银行软件开发中心工作";
    }
}
