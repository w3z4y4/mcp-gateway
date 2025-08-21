docker run -d --name mcp-db-container \
  -e MYSQL_ROOT_PASSWORD=McpDB123 \
  -e MYSQL_ROOT_HOST=% \
  -p 3306:3306 \
  -v /Users/macpro/work/mcp/mcp-gateway-db/data:/var/lib/mysql \
  -v /Users/macpro/work/mcp/mcp-gateway-db/conf:/etc/mysql/conf.d \
  mysql:8.0.43