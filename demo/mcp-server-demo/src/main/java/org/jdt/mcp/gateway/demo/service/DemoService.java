package org.jdt.mcp.gateway.demo.service;


import entity.Employee;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

@Service
public class DemoService {
//    @Tool(description = "根据姓名查询工作单位")
//    public String getJobs(@ToolParam(description = "姓名") String name){
//        return name+"在工商银行软件开发中心工作";
//    }

    @Tool(description = "根据姓名和手机号查询工作单位")
    public String getWorks(@ToolParam(description = "输入姓名和手机号") Employee employee){
        return employee.getName()+"在工商银行软件开发中心工作";
    }
}
