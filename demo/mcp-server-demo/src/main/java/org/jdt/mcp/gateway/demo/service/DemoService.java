package org.jdt.mcp.gateway.demo.service;


import entity.Employee;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class DemoService {

    // todo 能否提供流式返回
    // todo 调整mcp服务表的字段描述和mcp区分开
    @Tool(description = "根据姓名和手机号查询工作单位")
    public String getWorks(@ToolParam(description = "输入姓名和手机号") Employee employee){
        return employee.getName()+"在工商银行软件开发中心工作";
    }

}
