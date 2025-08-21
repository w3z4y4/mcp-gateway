package org.jdt.mcp.gateway.demo.service;


import entity.Employee;
import lombok.extern.slf4j.Slf4j;
import org.jdt.mcp.gateway.atuh.AuthContext;
import org.jdt.mcp.gateway.atuh.AuthContextHelper;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class DemoService {

    private final AuthContextHelper authContextHelper;
    public DemoService(AuthContextHelper authContextHelper){
        this.authContextHelper = authContextHelper;
    }

    // todo 能否提供流式返回
    @Tool(description = "根据姓名和手机号查询工作单位")
    public String getWorks(@ToolParam(description = "输入姓名和手机号") Employee employee){
        AuthContextHelper.AuthInfo authInfo = authContextHelper.getAuthInfoSync();

        log.info("mcp server get authKey {}, connectionId {}",
                authInfo.authKey(), authInfo.connectionId());
        return employee.getName()+"在工商银行软件开发中心工作";
    }

}
